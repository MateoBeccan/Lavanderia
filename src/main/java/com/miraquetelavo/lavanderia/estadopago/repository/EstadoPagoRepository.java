package com.miraquetelavo.lavanderia.estadopago.repository;

import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EstadoPagoRepository extends JpaRepository<EstadoPago, Long> {

    List<EstadoPago> findByActivoTrueOrderByOrdenAscIdAsc();

    Optional<EstadoPago> findByIdAndActivoTrue(Long id);

    Optional<EstadoPago> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Modifying
    @Query(value = """
            insert into {h-schema}estados_pago (codigo, nombre, descripcion, orden)
            values ('PENDIENTE', 'Pendiente', 'El importe total está pendiente.', 1),
                   ('PARCIAL', 'Parcialmente pagado', 'Se ha pagado una parte del importe total.', 2),
                   ('PAGADO', 'Pagado', 'Se ha pagado el importe total.', 3)
            on conflict (codigo) do nothing
            """, nativeQuery = true)
    void inicializar();
}
