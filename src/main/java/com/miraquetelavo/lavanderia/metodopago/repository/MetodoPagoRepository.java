package com.miraquetelavo.lavanderia.metodopago.repository;

import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {

    @Query("""
            select metodo from MetodoPago metodo
            where metodo.activo = true
            order by case metodo.codigo
                when 'EFECTIVO' then 1
                when 'TRANSFERENCIA' then 2
                when 'TARJETA' then 3
                when 'OTROS' then 4
                else 5
            end, metodo.codigo
            """)
    List<MetodoPago> listarActivosEnOrdenComercial();

    Optional<MetodoPago> findByIdAndActivoTrue(Long id);

    Optional<MetodoPago> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Modifying
    @Query(value = """
            insert into {h-schema}metodos_pago (codigo, nombre)
            values ('EFECTIVO', 'Efectivo'),
                   ('TRANSFERENCIA', 'Transferencia bancaria'),
                   ('TARJETA', 'Tarjeta'),
                   ('OTROS', 'Otros')
            on conflict (codigo) do nothing
            """, nativeQuery = true)
    void inicializar();
}
