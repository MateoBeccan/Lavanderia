package com.miraquetelavo.lavanderia.cobro.service;

import com.miraquetelavo.lavanderia.cobro.dto.CobroResponse;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.dto.SaldoPedidoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CobroService {
    CobroResponse registrar(Long pedidoId, @NotNull @Valid RegistrarCobroRequest solicitud,
                            @NotNull @Positive Long usuarioId);

    CobroResponse obtener(Long id);

    Page<CobroResponse> listar(Long pedidoId, Pageable pageable);

    SaldoPedidoResponse consultarSaldo(Long pedidoId);
}
