package com.miraquetelavo.lavanderia.proveedorinsumo.repository;

import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.entity.ProveedorInsumo;
import com.miraquetelavo.lavanderia.proveedorinsumo.entity.ProveedorInsumoId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProveedorInsumoRepository extends JpaRepository<ProveedorInsumo, ProveedorInsumoId> {

    String VIGENTES = """
             from ProveedorInsumo relacion
             join relacion.proveedor proveedor
             join relacion.insumo insumo
             join insumo.categoria categoria
             where proveedor.activo = true and insumo.activo = true and categoria.activo = true
            """;

    @Query(value = """
            select new com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse(
                proveedor.id, proveedor.nombre, insumo.id, insumo.nombre, categoria.id, categoria.nombre)
            """ + VIGENTES + " and proveedor.id = :proveedorId order by insumo.id",
            countQuery = "select count(relacion)" + VIGENTES + " and proveedor.id = :proveedorId")
    Page<ProveedorInsumoResponse> listarInsumos(Long proveedorId, Pageable pageable);

    @Query(value = """
            select new com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse(
                proveedor.id, proveedor.nombre, proveedor.cuit, proveedor.rubro)
            """ + VIGENTES + " and insumo.id = :insumoId order by proveedor.id",
            countQuery = "select count(relacion)" + VIGENTES + " and insumo.id = :insumoId")
    Page<ProveedorResumenResponse> listarProveedores(Long insumoId, Pageable pageable);

    @Modifying
    @Query(value = """
            insert into {h-schema}proveedores_insumos (id_proveedor, id_insumo) values (:proveedorId, :insumoId)
            on conflict (id_proveedor, id_insumo) do nothing
            """, nativeQuery = true)
    int insertar(Long proveedorId, Long insumoId);

    @Modifying
    @Query("delete from ProveedorInsumo relacion where relacion.id = :id")
    void eliminar(ProveedorInsumoId id);
}
