package com.miraquetelavo.lavanderia.cliente.service.impl;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.dto.ClienteResponse;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cliente.service.ClienteService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Service
@Validated
@Transactional(readOnly = true)
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository repository;

    public ClienteServiceImpl(ClienteRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ClienteResponse registrar(ClienteRequest solicitud) {
        var cliente = new Cliente(solicitud.nombre(), solicitud.apellido(), solicitud.dni(),
                solicitud.telefono(), solicitud.email());
        return guardar(cliente);
    }

    @Override
    public ClienteResponse obtener(Long id) {
        return repository.findById(id)
                .filter(Cliente::isActivo)
                .map(ClienteServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public Page<ClienteResponse> listarActivos(Pageable pageable) {
        return repository.findByActivoTrue(pageable).map(ClienteServiceImpl::responder);
    }

    @Override
    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest solicitud) {
        var cliente = repository.findOneById(id)
                .filter(Cliente::isActivo)
                .orElseThrow(() -> noEncontrado(id));

        cliente.actualizarDatos(solicitud.nombre(), solicitud.apellido(), solicitud.dni(),
                solicitud.telefono(), solicitud.email());
        return guardar(cliente);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        var cliente = repository.findOneById(id).orElseThrow(() -> noEncontrado(id));
        cliente.desactivar();
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el cliente con ID " + id + ".");
    }

    private ClienteResponse guardar(Cliente cliente) {
        try {
            return responder(repository.saveAndFlush(cliente));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Cliente.UK_DNI)) {
                throw new ConflictoException("Ya existe un cliente con ese DNI.", ex);
            }
            throw ex;
        }
    }

    private static ClienteResponse responder(Cliente cliente) {
        return new ClienteResponse(cliente.getId(), cliente.getNombre(), cliente.getApellido(), cliente.getDni(),
                cliente.getTelefono(), cliente.getEmail(), cliente.isActivo(), cliente.getFechaCreacion());
    }
}
