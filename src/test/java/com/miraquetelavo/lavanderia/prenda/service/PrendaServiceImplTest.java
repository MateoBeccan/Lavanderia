package com.miraquetelavo.lavanderia.prenda.service;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaRequest;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.prenda.service.impl.PrendaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrendaServiceImplTest {

    private static final long ID = 7L;
    private static final PrendaRequest SOLICITUD = new PrendaRequest(" CAMISA ");

    @Mock
    private PrendaRepository repository;

    @InjectMocks
    private PrendaServiceImpl service;

    @Test
    void registraActivoYNormalizaLosExtremosDelNombre() {
        when(repository.saveAndFlush(any(Prenda.class))).thenAnswer(invocacion -> {
            Prenda prenda = invocacion.getArgument(0);
            ReflectionTestUtils.setField(prenda, "id", ID);
            return prenda;
        });

        var respuesta = service.registrar(SOLICITUD);

        verify(repository).existeNombre("CAMISA");
        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("CAMISA");
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void rechazaUnNombreQueYaExisteAlRegistrar() {
        when(repository.existeNombre("CAMISA")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(SOLICITUD)).isInstanceOf(ConflictoException.class);

        verify(repository).existeNombre("CAMISA");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void obtieneUnaPrendaActiva() {
        when(repository.findById(ID)).thenReturn(Optional.of(prenda()));

        assertThat(service.obtener(ID).id()).isEqualTo(ID);
    }

    @Test
    void devuelveNoEncontradoParaIdInexistente() {
        assertThatThrownBy(() -> service.obtener(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.desactivar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.activar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listaActivosConPaginacion() {
        var pagina = PageRequest.of(1, 1);
        when(repository.findByActivoTrue(pagina)).thenReturn(new PageImpl<>(List.of(prenda()), pagina, 3));

        var respuesta = service.listarActivos(pagina);

        assertThat(respuesta.getNumber()).isEqualTo(1);
        assertThat(respuesta.getTotalElements()).isEqualTo(3);
        assertThat(respuesta.getContent()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(ID));
    }

    @Test
    void actualizaElNombreYConservaElIdYElEstado() {
        var prenda = prenda();
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));
        when(repository.saveAndFlush(prenda)).thenReturn(prenda);

        var respuesta = service.actualizar(ID, SOLICITUD);

        verify(repository).existeNombreEnOtraPrenda("CAMISA", ID);
        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("CAMISA");
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void conservaElNombrePropioAlActualizar() {
        var prenda = prenda();
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));
        when(repository.saveAndFlush(prenda)).thenReturn(prenda);

        var respuesta = service.actualizar(ID, new PrendaRequest(prenda.getNombre()));

        verify(repository).existeNombreEnOtraPrenda("Pantalón", ID);
        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("Pantalón");
    }

    @Test
    void rechazaActualizarSiOtraPrendaTieneElNombre() {
        var prenda = prenda();
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));
        when(repository.existeNombreEnOtraPrenda("CAMISA", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD)).isInstanceOf(ConflictoException.class);

        assertThat(prenda.getNombre()).isEqualTo("Pantalón");
    }

    @Test
    void traduceLaColisionDeUnicidadReportadaPorPostgresqlAlGuardar() {
        var error = errorDeIntegridad("23505", Prenda.UK_NOMBRE);
        when(repository.saveAndFlush(any(Prenda.class))).thenThrow(error);
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda()));

        assertThatThrownBy(() -> service.registrar(SOLICITUD))
                .isInstanceOf(ConflictoException.class).hasCause(error);
        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD))
                .isInstanceOf(ConflictoException.class).hasCause(error);
    }

    @ParameterizedTest
    @CsvSource({"23505, prendas_pkey", "23514, uk_prendas_nombre"})
    void conservaOtrosErroresDeIntegridad(String estado, String restriccion) {
        var error = errorDeIntegridad(estado, restriccion);
        when(repository.saveAndFlush(any(Prenda.class))).thenThrow(error);

        assertThatThrownBy(() -> service.registrar(SOLICITUD)).isSameAs(error);
    }

    @Test
    void desactivaSinBorrarYPermiteRepetirLaBaja() {
        var prenda = prenda();
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));

        service.desactivar(ID);
        assertThat(prenda.getActivo()).isFalse();
        service.desactivar(ID);
        assertThat(prenda.getActivo()).isFalse();

        verify(repository, times(2)).findOneById(ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void ocultaInactivosYRechazaActualizarlos() {
        var prenda = prenda();
        prenda.desactivar();
        when(repository.findById(ID)).thenReturn(Optional.of(prenda));
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));

        assertThatThrownBy(() -> service.obtener(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(prenda.getNombre()).isEqualTo("Pantalón");
    }

    @Test
    void reactivaElMismoRegistroYPermiteRepetirLaOperacion() {
        var prenda = prenda();
        prenda.desactivar();
        when(repository.findOneById(ID)).thenReturn(Optional.of(prenda));

        var respuesta = service.activar(ID);

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.activo()).isTrue();
        assertThat(service.activar(ID)).isEqualTo(respuesta);
        assertThat(prenda.getActivo()).isTrue();
    }

    private static DataIntegrityViolationException errorDeIntegridad(String estado, String restriccion) {
        var mensaje = new ServerErrorMessage(
                "SERROR\0C" + estado + "\0MSe violó una restricción de integridad.\0n" + restriccion + "\0\0");
        return new DataIntegrityViolationException("Error de integridad", new PSQLException(mensaje));
    }

    private static Prenda prenda() {
        var prenda = new Prenda("Pantalón");
        ReflectionTestUtils.setField(prenda, "id", ID);
        return prenda;
    }
}
