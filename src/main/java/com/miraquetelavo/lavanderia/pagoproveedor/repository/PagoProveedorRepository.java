package com.miraquetelavo.lavanderia.pagoproveedor.repository;

import com.miraquetelavo.lavanderia.pagoproveedor.entity.PagoProveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.math.BigDecimal;
import java.util.Optional;

public interface PagoProveedorRepository extends Repository<PagoProveedor, Long> {
    PagoProveedor save(PagoProveedor pago);

    @EntityGraph(attributePaths = {"compra.proveedor", "movimientoCaja.metodoPago", "movimientoCaja.usuario"})
    Optional<PagoProveedor> findDetalleById(Long id);

    @EntityGraph(attributePaths = {"compra.proveedor", "movimientoCaja.metodoPago", "movimientoCaja.usuario"})
    Page<PagoProveedor> findByCompraIdOrderByFechaPagoDescIdDesc(Long compraId, Pageable pageable);

    boolean existsByCompraId(Long compraId);

    @Query("select coalesce(sum(c.montoPagado), 0.00) from PagoProveedor c where c.compra.id = :compraId")
    BigDecimal sumarPorCompra(Long compraId);

    // Una sola sentencia lee el total, el catálogo y todos los pagos de la misma versión confirmada.
    @Query("""
            select p.totalCompra as total, e.codigo as codigo, e.nombre as nombre,
                   coalesce(sum(c.montoPagado), 0.00) as pagado
            from Compra p
            left join p.estadoPago e
            left join PagoProveedor c on c.compra = p
            where p.id = :compraId
            group by p.totalCompra, e.codigo, e.nombre
            """)
    Optional<Saldo> consultarSaldo(Long compraId);

    interface Saldo {
        BigDecimal getTotal();
        String getCodigo();
        String getNombre();
        BigDecimal getPagado();
    }
}
