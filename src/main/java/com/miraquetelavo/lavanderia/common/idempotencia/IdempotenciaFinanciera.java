package com.miraquetelavo.lavanderia.common.idempotencia;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class IdempotenciaFinanciera {
    public static final String CABECERA = "Idempotency-Key";
    private static final Pattern FORMATO_UUID =
            Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final boolean claveObligatoria;

    public IdempotenciaFinanciera(JdbcTemplate jdbc, JsonMapper json,
                                 @Value("${lavanderia.idempotencia.financiera.requerida:false}") boolean claveObligatoria) {
        this.jdbc = jdbc;
        this.json = json;
        this.claveObligatoria = claveObligatoria;
    }

    public enum Operacion {
        COBRO, PAGO_PROVEEDOR, APERTURA_CAJA, INGRESO_MANUAL, EGRESO_MANUAL
    }

    public record Contenido(Long destinoId, BigDecimal monto, Long metodoPagoId, String descripcion) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> ResponseEntity<T> crear(HttpHeaders cabeceras, long usuarioId, Operacion operacion,
                                       Contenido contenido, Class<T> tipo, Supplier<ResponseEntity<T>> ejecutar) {
        var valores = cabeceras.get(CABECERA);
        if (valores == null && !claveObligatoria) {
            return ejecutar.get();
        }
        if (valores == null || valores.size() != 1 || !FORMATO_UUID.matcher(valores.getFirst()).matches()) {
            throw new SolicitudInvalidaException("Se requiere una única cabecera Idempotency-Key con formato UUID.");
        }

        var clave = UUID.fromString(valores.getFirst());
        var huella = huella(contenido);
        // El orden es clave idempotente → Caja → Pedido/Compra. El bloqueo termina con la transacción.
        var bloqueo = "lavanderia:finanzas:" + usuarioId + ":" + clave;
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(hashtextextended(current_schema() || ':' || ?, 0))",
                Boolean.class, bloqueo))) {
            throw new ConflictoIdempotencia(ConflictoIdempotencia.Motivo.EN_CURSO);
        }

        var anterior = jdbc.query("""
                select operacion, huella, ubicacion, cuerpo
                from solicitudes_financieras_idempotentes
                where usuario_id = ? and clave = ?
                """, (rs, fila) -> new Resultado(rs.getString("operacion"), rs.getString("huella"),
                rs.getString("ubicacion"), rs.getString("cuerpo")), usuarioId, clave).stream().findFirst();

        if (anterior.isPresent()) {
            var resultado = anterior.get();
            if (!operacion.name().equals(resultado.operacion()) || !(huella.equals(resultado.huella())
                    || coincideMovimientoManualHistorico(operacion, contenido, resultado))) {
                throw new ConflictoIdempotencia(ConflictoIdempotencia.Motivo.CLAVE_REUTILIZADA);
            }
            return ResponseEntity.created(URI.create(resultado.ubicacion())).body(json.readValue(resultado.cuerpo(), tipo));
        }

        var respuesta = ejecutar.get();
        jdbc.update("""
                insert into solicitudes_financieras_idempotentes
                    (usuario_id, clave, operacion, huella, ubicacion, cuerpo)
                values (?, ?, ?, ?, ?, ?)
                """, usuarioId, clave, operacion.name(), huella,
                respuesta.getHeaders().getLocation().toString(), json.writeValueAsString(respuesta.getBody()));
        return respuesta;
    }

    private boolean coincideMovimientoManualHistorico(Operacion operacion, Contenido contenido, Resultado resultado) {
        if ((operacion != Operacion.INGRESO_MANUAL && operacion != Operacion.EGRESO_MANUAL)
                || contenido.destinoId() == null) {
            return false;
        }
        // V032 guardaba el destino de los movimientos manuales solamente en la respuesta confirmada.
        var legado = new Contenido(null, contenido.monto(), contenido.metodoPagoId(), contenido.descripcion());
        if (!huella(legado).equals(resultado.huella())) {
            return false;
        }
        var cajaId = json.readTree(resultado.cuerpo()).path("cajaId");
        return cajaId.isIntegralNumber() && cajaId.canConvertToLong() && cajaId.longValue() == contenido.destinoId();
    }

    private static String huella(Contenido contenido) {
        try {
            // Los tres primeros valores son numéricos o vacíos. La descripción ocupa el resto.
            var canonico = String.join("\n", "v1", Objects.toString(contenido.destinoId(), ""),
                    contenido.monto().stripTrailingZeros().toPlainString(),
                    Objects.toString(contenido.metodoPagoId(), ""), Objects.toString(contenido.descripcion(), ""));
            var bytes = canonico.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible.", ex);
        }
    }

    private record Resultado(String operacion, String huella, String ubicacion, String cuerpo) {
    }
}
