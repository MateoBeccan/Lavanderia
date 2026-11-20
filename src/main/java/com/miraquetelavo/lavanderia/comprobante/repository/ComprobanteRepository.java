package com.miraquetelavo.lavanderia.comprobante.repository;

import com.miraquetelavo.lavanderia.comprobante.entity.Comprobante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface ComprobanteRepository extends Repository<Comprobante, Long> {
    Comprobante saveAndFlush(Comprobante comprobante);

    @EntityGraph(attributePaths = {"cobro.movimientoCaja.metodoPago", "pagoProveedor.movimientoCaja.metodoPago"})
    Optional<Comprobante> findById(Long id);

    @EntityGraph(attributePaths = "cobro.movimientoCaja.metodoPago")
    Optional<Comprobante> findByCobroId(Long cobroId);

    @EntityGraph(attributePaths = "pagoProveedor.movimientoCaja.metodoPago")
    Optional<Comprobante> findByPagoProveedorId(Long pagoId);

    boolean existsByCobroId(Long cobroId);
    boolean existsByPagoProveedorId(Long pagoId);

    @EntityGraph(attributePaths = "cobro")
    Page<Comprobante> findByCobroPedidoIdOrderByFechaEmisionDescIdDesc(Long pedidoId, Pageable pageable);

    @EntityGraph(attributePaths = "pagoProveedor")
    Page<Comprobante> findByPagoProveedorCompraIdOrderByFechaEmisionDescIdDesc(Long compraId, Pageable pageable);
}
