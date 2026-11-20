package com.miraquetelavo.lavanderia.estadopedido.repository;

import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstadoPedidoRepository extends JpaRepository<EstadoPedido, Long> {

    List<EstadoPedido> findByActivoTrueOrderByOrdenAscIdAsc();

    Optional<EstadoPedido> findByIdAndActivoTrue(Long id);

    Optional<EstadoPedido> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Modifying
    @Query(value = """
            insert into {h-schema}estados_pedido (codigo, nombre, descripcion, orden)
            values (:codigo, :nombre, :descripcion, :orden)
            on conflict (codigo) do nothing
            """, nativeQuery = true)
    void insertarSiNoExiste(@Param("codigo") String codigo, @Param("nombre") String nombre,
                            @Param("descripcion") String descripcion, @Param("orden") int orden);
}
