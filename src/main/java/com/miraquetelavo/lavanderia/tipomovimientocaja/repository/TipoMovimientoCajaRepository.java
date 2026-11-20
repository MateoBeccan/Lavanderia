package com.miraquetelavo.lavanderia.tipomovimientocaja.repository;

import com.miraquetelavo.lavanderia.tipomovimientocaja.entity.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TipoMovimientoCajaRepository extends JpaRepository<TipoMovimientoCaja, Long> {

    List<TipoMovimientoCaja> findByActivoTrueOrderByOrdenAscIdAsc();

    Optional<TipoMovimientoCaja> findByIdAndActivoTrue(Long id);

    Optional<TipoMovimientoCaja> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Modifying
    @Query(value = "lock table {h-schema}tipos_movimiento_caja in share row exclusive mode", nativeQuery = true)
    void bloquearParaInicializar();

    @Modifying
    @Query(value = """
            insert into {h-schema}tipos_movimiento_caja (codigo, nombre, descripcion, orden)
            values ('INGRESO_MANUAL', 'Ingreso manual', 'Ingreso de dinero registrado manualmente.', 1),
                   ('EGRESO_MANUAL', 'Egreso manual', 'Salida de dinero registrada manualmente.', 2),
                   ('COBRO_PEDIDO', 'Cobro pedido', 'Ingreso asociado al cobro de un pedido.', 3),
                   ('PAGO_PROVEEDOR', 'Pago proveedor', 'Egreso asociado al pago de una compra a proveedor.', 4)
            on conflict (codigo) do nothing
            """, nativeQuery = true)
    void inicializar();
}
