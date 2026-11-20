package com.miraquetelavo.lavanderia.cobro.repository;

import com.miraquetelavo.lavanderia.cobro.entity.Cobro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.math.BigDecimal;
import java.util.Optional;

public interface CobroRepository extends Repository<Cobro, Long> {
    Cobro save(Cobro cobro);

    @EntityGraph(attributePaths = {"movimientoCaja.metodoPago", "movimientoCaja.usuario"})
    Optional<Cobro> findDetalleById(Long id);

    @EntityGraph(attributePaths = {"movimientoCaja.metodoPago", "movimientoCaja.usuario"})
    Page<Cobro> findByPedidoIdOrderByFechaCobroDescIdDesc(Long pedidoId, Pageable pageable);

    boolean existsByPedidoId(Long pedidoId);

    @Query("select coalesce(sum(c.montoCobrado), 0.00) from Cobro c where c.pedido.id = :pedidoId")
    BigDecimal sumarPorPedido(Long pedidoId);

    // Una sola sentencia lee el total, el catálogo y todos los cobros de la misma versión confirmada.
    @Query("""
            select p.total as total, e.codigo as codigo, e.nombre as nombre,
                   coalesce(sum(c.montoCobrado), 0.00) as cobrado
            from Pedido p
            left join p.estadoPago e
            left join Cobro c on c.pedido = p
            where p.id = :pedidoId
            group by p.total, e.codigo, e.nombre
            """)
    Optional<Saldo> consultarSaldo(Long pedidoId);

    interface Saldo {
        BigDecimal getTotal();
        String getCodigo();
        String getNombre();
        BigDecimal getCobrado();
    }
}
