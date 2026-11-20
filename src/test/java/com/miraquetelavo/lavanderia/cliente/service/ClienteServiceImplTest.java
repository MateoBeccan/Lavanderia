package com.miraquetelavo.lavanderia.cliente.service;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cliente.service.impl.ClienteServiceImpl;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ClienteServiceImplTest {

    private static final long ID = 7L;

    @Mock
    private ClienteRepository repository;

    @InjectMocks
    private ClienteServiceImpl service;

    @Test
    void registraActivoConFechaYNormalizaLosDatos() {
        when(repository.saveAndFlush(any(Cliente.class))).thenAnswer(invocacion -> {
            Cliente cliente = invocacion.getArgument(0);
            ReflectionTestUtils.setField(cliente, "id", ID);
            return cliente;
        });

        var respuesta = service.registrar(new ClienteRequest(" Ana ", " Pérez ", "1234567", " ", null));

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.nombre()).isEqualTo("Ana");
        assertThat(respuesta.apellido()).isEqualTo("Pérez");
        assertThat(respuesta.dni()).isEqualTo("1234567");
        assertThat(respuesta.telefono()).isNull();
        assertThat(respuesta.email()).isNull();
        assertThat(respuesta.activo()).isTrue();
        assertThat(respuesta.fechaCreacion()).isNotNull();
    }

    @Test
    void obtieneUnClienteActivo() {
        when(repository.findById(ID)).thenReturn(Optional.of(cliente()));

        assertThat(service.obtener(ID).id()).isEqualTo(ID);
    }

    @Test
    void rechazaOperacionesSobreUnIdInexistente() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        when(repository.findOneById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, datosNuevos())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.desactivar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listaActivosYConservaLaPaginacion() {
        var pagina = PageRequest.of(1, 1);
        when(repository.findByActivoTrue(pagina)).thenReturn(new PageImpl<>(List.of(cliente()), pagina, 3));

        var respuesta = service.listarActivos(pagina);

        assertThat(respuesta.getNumber()).isEqualTo(1);
        assertThat(respuesta.getTotalElements()).isEqualTo(3);
        assertThat(respuesta.getContent()).singleElement().satisfies(cliente -> assertThat(cliente.id()).isEqualTo(ID));
    }

    @Test
    void actualizaDatosConservandoIdentidadYFecha() {
        var cliente = cliente();
        var fechaCreacion = cliente.getFechaCreacion();
        when(repository.findOneById(ID)).thenReturn(Optional.of(cliente));
        when(repository.saveAndFlush(cliente)).thenReturn(cliente);

        var respuesta = service.actualizar(ID, datosNuevos());

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.fechaCreacion()).isEqualTo(fechaCreacion);
        assertThat(respuesta.nombre()).isEqualTo("Eva");
        assertThat(respuesta.apellido()).isEqualTo("García");
        assertThat(respuesta.dni()).isEqualTo("12345678");
        assertThat(respuesta.telefono()).isEqualTo("123");
        assertThat(respuesta.email()).isNull();
    }

    @Test
    void informaConflictoCuandoLaBaseRechazaElDniDuplicado() {
        when(repository.saveAndFlush(any(Cliente.class)))
                .thenThrow(errorDeIntegridad("23505", Cliente.UK_DNI));
        when(repository.findOneById(ID)).thenReturn(Optional.of(cliente()));

        assertThatThrownBy(() -> service.registrar(datosNuevos())).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, datosNuevos())).isInstanceOf(ConflictoException.class);
    }

    @Test
    void permiteConservarElDniPropio() {
        var cliente = cliente();
        var dni = cliente.getDni();
        when(repository.findOneById(ID)).thenReturn(Optional.of(cliente));
        when(repository.saveAndFlush(cliente)).thenReturn(cliente);

        var respuesta = service.actualizar(ID, new ClienteRequest("Eva", "García", dni, null, null));

        assertThat(respuesta.id()).isEqualTo(ID);
        assertThat(respuesta.dni()).isEqualTo(dni);
    }

    @Test
    void conservaElErrorSiFallaOtraRestriccionUnique() {
        var error = errorDeIntegridad("23505", "clientes_pkey");
        when(repository.saveAndFlush(any(Cliente.class))).thenThrow(error);

        assertThatThrownBy(() -> service.registrar(datosNuevos())).isSameAs(error);
    }

    @Test
    void conservaElErrorSiElCodigoSqlNoEsDeUnicidad() {
        var error = errorDeIntegridad("23514", Cliente.UK_DNI);
        when(repository.saveAndFlush(any(Cliente.class))).thenThrow(error);

        assertThatThrownBy(() -> service.registrar(datosNuevos())).isSameAs(error);
    }

    private DataIntegrityViolationException errorDeIntegridad(String estado, String restriccion) {
        var mensaje = new ServerErrorMessage(
                "SERROR\0C" + estado + "\0MSe violó una restricción de integridad.\0n" + restriccion + "\0\0");
        return new DataIntegrityViolationException("Error de integridad", new PSQLException(mensaje));
    }

    @Test
    void desactivaSinBorrarYAdmiteRepetirLaBaja() {
        var cliente = cliente();
        when(repository.findOneById(ID)).thenReturn(Optional.of(cliente));

        service.desactivar(ID);
        service.desactivar(ID);

        assertThat(cliente.isActivo()).isFalse();
        verify(repository, times(2)).findOneById(ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void ocultaYRechazaActualizarUnClienteInactivo() {
        var cliente = cliente();
        cliente.desactivar();
        when(repository.findById(ID)).thenReturn(Optional.of(cliente));
        when(repository.findOneById(ID)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.obtener(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizar(ID, datosNuevos())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cliente.getNombre()).isEqualTo("Ana");
    }

    private Cliente cliente() {
        var cliente = new Cliente("Ana", "Pérez", "01234567", null, "ana@example.com");
        ReflectionTestUtils.setField(cliente, "id", ID);
        return cliente;
    }

    private ClienteRequest datosNuevos() {
        return new ClienteRequest("Eva", "García", "12345678", " 123 ", null);
    }
}
