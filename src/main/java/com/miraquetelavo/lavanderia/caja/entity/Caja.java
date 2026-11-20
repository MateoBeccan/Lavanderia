package com.miraquetelavo.lavanderia.caja.entity;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas",
        indexes = @Index(name = "ix_cajas_fecha_apertura_id", columnList = "fecha_apertura desc, id_caja desc"),
        check = {
                @CheckConstraint(name = "ck_cajas_importes", constraint = Caja.CHECK_IMPORTES),
                @CheckConstraint(name = "ck_cajas_cierre", constraint = """
        (estado = 'ABIERTA' and id_usuario_cierre is null and fecha_cierre is null
            and monto_final_calc is null and efectivo_esperado is null and efectivo_contado is null
            and diferencia_arqueo is null)
        or (estado = 'CERRADA' and id_usuario_cierre is not null and fecha_cierre is not null
            and monto_final_calc is not null and efectivo_esperado is not null and efectivo_contado is not null
            and diferencia_arqueo is not null)
        """)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Caja {

    public static final String CHECK_IMPORTES = """
            monto_inicial >= 0 and efectivo_esperado >= 0 and efectivo_contado >= 0
            and diferencia_arqueo = efectivo_contado - efectivo_esperado
            """;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caja")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_apertura", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cajas_usuario_apertura"))
    private Usuario usuarioApertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cierre", foreignKey = @ForeignKey(name = "fk_cajas_usuario_cierre"))
    private Usuario usuarioCierre;

    @Column(name = "fecha_apertura", nullable = false, updatable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "monto_inicial", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montoInicial;

    @Column(name = "monto_final_calc", precision = 10, scale = 2)
    private BigDecimal montoFinalCalculado;

    @Column(name = "efectivo_esperado", precision = 10, scale = 2)
    private BigDecimal efectivoEsperado;

    @Column(name = "efectivo_contado", precision = 10, scale = 2)
    private BigDecimal efectivoContado;

    @Column(name = "diferencia_arqueo", precision = 11, scale = 2)
    private BigDecimal diferenciaArqueo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCaja estado;

    public void cerrar(Usuario administrador, LocalDateTime fecha, BigDecimal contado,
                       BigDecimal saldoGeneral, BigDecimal esperado) {
        if (estado != EstadoCaja.ABIERTA) {
            throw new ConflictoException("La caja ya está cerrada.", null);
        }
        usuarioCierre = administrador;
        fechaCierre = fecha;
        efectivoContado = contado;
        montoFinalCalculado = saldoGeneral;
        efectivoEsperado = esperado;
        diferenciaArqueo = contado.subtract(esperado);
        estado = EstadoCaja.CERRADA;
    }
}
