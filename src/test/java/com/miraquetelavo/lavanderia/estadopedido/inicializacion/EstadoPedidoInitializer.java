package com.miraquetelavo.lavanderia.estadopedido.inicializacion;

import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("test-postgresql")
public class EstadoPedidoInitializer implements ApplicationRunner {

    private final EstadoPedidoRepository repository;

    public EstadoPedidoInitializer(EstadoPedidoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.insertarSiNoExiste("PENDIENTE", "Pendiente",
                "Pedido registrado, pendiente de iniciar su procesamiento.", 1);
        repository.insertarSiNoExiste("EN_PROCESO", "En proceso",
                "Pedido que se encuentra siendo procesado.", 2);
        repository.insertarSiNoExiste("LISTO", "Listo para entregar",
                "Pedido finalizado y disponible para su entrega.", 3);
        repository.insertarSiNoExiste("ENTREGADO", "Entregado",
                "Pedido entregado al cliente.", 4);
        repository.insertarSiNoExiste("CANCELADO", "Cancelado",
                "Pedido cuya operación fue cancelada.", 5);
    }
}
