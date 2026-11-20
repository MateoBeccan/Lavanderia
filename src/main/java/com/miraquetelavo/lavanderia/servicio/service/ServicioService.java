package com.miraquetelavo.lavanderia.servicio.service;

import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServicioService {

    ServicioResponse registrar(@NotNull @Valid ServicioRequest solicitud);

    ServicioResponse obtener(Long id);

    Page<ServicioResponse> listarActivos(Pageable pageable);

    ServicioResponse actualizar(Long id, @NotNull @Valid ServicioRequest solicitud);

    void desactivar(Long id);

    ServicioResponse activar(Long id);
}
