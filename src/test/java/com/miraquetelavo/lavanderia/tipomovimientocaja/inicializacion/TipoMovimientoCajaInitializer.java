package com.miraquetelavo.lavanderia.tipomovimientocaja.inicializacion;

import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Component
@RequiredArgsConstructor
@Profile("test-postgresql")
public class TipoMovimientoCajaInitializer implements ApplicationRunner {

    private final TipoMovimientoCajaRepository tipos;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // El conflicto por código no arbitra la restricción única de nombre.
        tipos.bloquearParaInicializar();
        try {
            tipos.inicializar();
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, "uk_tipos_movimiento_caja_nombre")) {
                throw new IllegalStateException("Un nombre inicial de TipoMovimientoCaja está asignado a otro código.", ex);
            }
            throw ex;
        }
    }
}
