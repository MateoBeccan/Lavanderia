package com.miraquetelavo.lavanderia.servicio.service;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.servicio.service.impl.ServicioServiceImpl;
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

import java.math.BigDecimal;
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
class ServicioServiceImplTest {

    private static final long ID = 7L;
    private static final BigDecimal PRECIO = new BigDecimal("1200.50");
    private static final ServicioRequest SOLICITUD = new ServicioRequest(" LAVADO ", PRECIO, ModalidadCobro.KILO);

    @Mock
    private ServicioRepository repository;

    @InjectMocks
    private ServicioServiceImpl service;

    @Test
    void registraActivoYNormalizaLosExtremosDelNombre() {
        when(repository.saveAndFlush(any(Servicio.class))).thenAnswer(invocacion -> {
            Servicio servicio = invocacion.getArgument(0);
            ReflectionTestUtils.setField(servicio, "id", ID);
            return servicio;
        });

        var respuesta = service.registrar(SOLICITUD);

        verify(repository).existeNombre("LAVADO");
        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("LAVADO");
        assertThat(respuesta.precio()).isEqualTo(PRECIO);
        assertThat(respuesta.modalidadCobro()).isEqualTo(ModalidadCobro.KILO);
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void rechazaUnNombreQueYaExisteAlRegistrar() {
        when(repository.existeNombre("LAVADO")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(SOLICITUD)).isInstanceOf(ConflictoException.class);

        verify(repository).existeNombre("LAVADO");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void obtieneUnServicioActivo() {
        when(repository.findById(ID)).thenReturn(Optional.of(servicio()));

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
        when(repository.findByActivoTrue(pagina)).thenReturn(new PageImpl<>(List.of(servicio()), pagina, 3));

        var respuesta = service.listarActivos(pagina);

        assertThat(respuesta.getNumber()).isEqualTo(1);
        assertThat(respuesta.getTotalElements()).isEqualTo(3);
        assertThat(respuesta.getContent()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(ID));
    }

    @Test
    void actualizaLosDatosYConservaElIdYElEstado() {
        var servicio = servicio();
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio));
        when(repository.saveAndFlush(servicio)).thenReturn(servicio);

        var respuesta = service.actualizar(ID, SOLICITUD);

        verify(repository).existeNombreEnOtroServicio("LAVADO", ID);
        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("LAVADO");
        assertThat(respuesta.precio()).isEqualTo(PRECIO);
        assertThat(respuesta.modalidadCobro()).isEqualTo(ModalidadCobro.KILO);
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void rechazaActualizarSiOtroServicioTieneElNombre() {
        var servicio = servicio();
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio));
        when(repository.existeNombreEnOtroServicio("LAVADO", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD)).isInstanceOf(ConflictoException.class);

        assertThat(servicio.getNombre()).isEqualTo("Secado");
    }

    @Test
    void traduceLaColisionDeUnicidadReportadaPorPostgresqlAlGuardar() {
        var error = errorDeIntegridad("23505", Servicio.UK_NOMBRE);
        when(repository.saveAndFlush(any(Servicio.class))).thenThrow(error);
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio()));

        assertThatThrownBy(() -> service.registrar(SOLICITUD))
                .isInstanceOf(ConflictoException.class).hasCause(error);
        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD))
                .isInstanceOf(ConflictoException.class).hasCause(error);
    }

    @ParameterizedTest
    @CsvSource({"23505, servicios_pkey", "23514, uk_servicios_nombre"})
    void conservaOtrosErroresDeIntegridad(String estado, String restriccion) {
        var error = errorDeIntegridad(estado, restriccion);
        when(repository.saveAndFlush(any(Servicio.class))).thenThrow(error);

        assertThatThrownBy(() -> service.registrar(SOLICITUD)).isSameAs(error);
    }

    @Test
    void desactivaSinBorrarYPermiteRepetirLaBaja() {
        var servicio = servicio();
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio));

        service.desactivar(ID);
        assertThat(servicio.getActivo()).isFalse();
        service.desactivar(ID);
        assertThat(servicio.getActivo()).isFalse();

        verify(repository, times(2)).findOneById(ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void ocultaInactivosYRechazaActualizarlos() {
        var servicio = servicio();
        servicio.desactivar();
        when(repository.findById(ID)).thenReturn(Optional.of(servicio));
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> service.obtener(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, SOLICITUD)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.getNombre()).isEqualTo("Secado");
    }

    @Test
    void reactivaElMismoRegistroYPermiteRepetirLaOperacion() {
        var servicio = servicio();
        servicio.desactivar();
        when(repository.findOneById(ID)).thenReturn(Optional.of(servicio));

        var respuesta = service.activar(ID);

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.activo()).isTrue();
        assertThat(service.activar(ID)).isEqualTo(respuesta);
        assertThat(servicio.getActivo()).isTrue();
    }

    private static DataIntegrityViolationException errorDeIntegridad(String estado, String restriccion) {
        var mensaje = new ServerErrorMessage(
                "SERROR\0C" + estado + "\0MSe violó una restricción de integridad.\0n" + restriccion + "\0\0");
        return new DataIntegrityViolationException("Error de integridad", new PSQLException(mensaje));
    }

    private static Servicio servicio() {
        var servicio = new Servicio("Secado", new BigDecimal("400.00"), ModalidadCobro.UNIDAD);
        ReflectionTestUtils.setField(servicio, "id", ID);
        return servicio;
    }
}
