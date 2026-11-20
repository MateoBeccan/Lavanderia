package com.miraquetelavo.lavanderia.movimientocaja.repository;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long>,
        JpaSpecificationExecutor<MovimientoCaja> {

    @Override
    @EntityGraph(attributePaths = {"tipoMovimiento", "metodoPago", "usuario"})
    Page<MovimientoCaja> findAll(Specification<MovimientoCaja> filtro, Pageable pageable);

    @EntityGraph(attributePaths = {"tipoMovimiento", "metodoPago", "usuario"})
    Optional<MovimientoCaja> findDetalleById(Long id);

    @Query("""
            select caja as caja,
                coalesce(sum(m.monto) filter (where tipo.codigo in :ingresos), 0.00) as ingresos,
                coalesce(sum(m.monto) filter (where tipo.codigo in :egresos), 0.00) as egresos,
                coalesce(sum(m.monto) filter (where tipo.codigo in :ingresos and metodo.codigo = :efectivo), 0.00)
                    as ingresosEfectivo,
                coalesce(sum(m.monto) filter (where tipo.codigo in :egresos and metodo.codigo = :efectivo), 0.00)
                    as egresosEfectivo,
                count(m.id) filter (where tipo.codigo not in :ingresos and tipo.codigo not in :egresos) as sinClasificar
            from Caja caja
            left join MovimientoCaja m on m.caja = caja
            left join m.tipoMovimiento tipo
            left join m.metodoPago metodo
            where caja.id = :id
            group by caja
            """)
    Optional<Resumen> resumir(Long id, Set<String> ingresos, Set<String> egresos, String efectivo);

    interface Resumen {
        Caja getCaja();
        BigDecimal getIngresos();
        BigDecimal getEgresos();
        BigDecimal getIngresosEfectivo();
        BigDecimal getEgresosEfectivo();
        long getSinClasificar();
    }
}
