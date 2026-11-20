package com.miraquetelavo.lavanderia.metodopago.inicializacion;

import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Component
@Profile("test-postgresql")
public class MetodoPagoInitializer implements ApplicationRunner {

    private final MetodoPagoRepository repository;

    public MetodoPagoInitializer(MetodoPagoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            repository.inicializar();
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, "uk_metodos_pago_nombre")) {
                throw new IllegalStateException(
                        "No se pudo inicializar MétodoPago: un nombre inicial está asignado a otro código.");
            }
            throw ex;
        }
    }
}
