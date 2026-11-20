package com.miraquetelavo.lavanderia.estadopago.inicializacion;

import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
@Profile("test-postgresql")
public class EstadoPagoInitializer implements ApplicationRunner {

    private final EstadoPagoRepository repository;

    public EstadoPagoInitializer(EstadoPagoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.inicializar();
    }
}
