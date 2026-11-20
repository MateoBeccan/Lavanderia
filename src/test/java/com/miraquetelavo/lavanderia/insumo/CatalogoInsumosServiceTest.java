package com.miraquetelavo.lavanderia.insumo;

import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.categoriainsumo.service.impl.CategoriaInsumoServiceImpl;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.insumo.service.impl.InsumoServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoInsumosServiceTest {

    @Mock
    private CategoriaInsumoRepository categorias;

    @Mock
    private InsumoRepository insumos;

    @Test
    void verificaInsumosActivosDespuesDeBloquearCategoria() {
        var categoria = new CategoriaInsumo("Detergentes");
        when(categorias.findOneById(7L)).thenReturn(Optional.of(categoria));
        when(insumos.existsByCategoriaIdAndActivoTrue(7L)).thenReturn(true);
        var servicio = new CategoriaInsumoServiceImpl(categorias, insumos);

        assertThatThrownBy(() -> servicio.desactivar(7L)).isInstanceOf(ConflictoException.class);
        assertThat(categoria.getActivo()).isTrue();
        var orden = inOrder(categorias, insumos);
        orden.verify(categorias).findOneById(7L);
        orden.verify(insumos).existsByCategoriaIdAndActivoTrue(7L);
    }

    @Test
    void bajaIdempotenteDeCategoriaInactiva() {
        var categoria = new CategoriaInsumo("Detergentes");
        categoria.desactivar();
        when(categorias.findOneById(7L)).thenReturn(Optional.of(categoria));
        var servicio = new CategoriaInsumoServiceImpl(categorias, insumos);

        servicio.desactivar(7L);

        assertThat(categoria.getActivo()).isFalse();
        verifyNoInteractions(insumos);
    }

    @Test
    void bloqueaInsumoYDespuesLaCategoriaDeDestinoAntesDeGuardar() {
        var origen = new CategoriaInsumo("Jabones");
        var destino = new CategoriaInsumo("Detergentes");
        var insumo = new Insumo("Jabón", origen);
        when(insumos.findOneById(9L)).thenReturn(Optional.of(insumo));
        when(categorias.bloquearParaInsumo(7L)).thenReturn(Optional.of(destino));
        when(insumos.saveAndFlush(any(Insumo.class))).thenReturn(insumo);
        var servicio = new InsumoServiceImpl(categorias, insumos);

        var respuesta = servicio.actualizar(9L, new InsumoRequest("  Detergente  ", 7L));

        assertThat(respuesta.nombre()).isEqualTo("Detergente");
        assertThat(respuesta.categoriaNombre()).isEqualTo("Detergentes");
        var orden = inOrder(insumos, categorias);
        orden.verify(insumos).findOneById(9L);
        orden.verify(categorias).bloquearParaInsumo(7L);
        orden.verify(insumos).saveAndFlush(insumo);
    }
}
