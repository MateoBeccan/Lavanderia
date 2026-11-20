package com.miraquetelavo.lavanderia.cliente.service;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.dto.ClienteResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClienteService {

    ClienteResponse registrar(@NotNull @Valid ClienteRequest solicitud);

    ClienteResponse obtener(Long id);

    Page<ClienteResponse> listarActivos(Pageable pageable);

    ClienteResponse actualizar(Long id, @NotNull @Valid ClienteRequest solicitud);

    void desactivar(Long id);
}
