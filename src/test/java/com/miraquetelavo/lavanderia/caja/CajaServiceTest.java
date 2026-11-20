package com.miraquetelavo.lavanderia.caja;

import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.caja.service.impl.CajaServiceImpl;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.movimientocaja.service.CalculadorSaldoCaja;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CajaServiceTest {

    private final CajaRepository cajas = mock(CajaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-17T12:30:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private final CajaServiceImpl servicio = new CajaServiceImpl(cajas, usuarios, reloj, mock(CalculadorSaldoCaja.class));

    @Test
    void rechazaLaAperturaSiLaCuentaYaNoEstaActiva() {
        when(usuarios.findByIdAndActivoTrue(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.abrirCaja(new AbrirCajaRequest(BigDecimal.ZERO), 1L))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(cajas);
    }

    @Test
    void usaElRelojDelNegocioYTraduceLaColisionDelIndice() {
        var usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(1L);
        when(usuarios.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuario));
        var fecha = LocalDateTime.of(2026, 11, 17, 9, 30);
        when(cajas.insertarAbierta(1L, fecha, BigDecimal.ZERO)).thenReturn(0);

        assertThatThrownBy(() -> servicio.abrirCaja(new AbrirCajaRequest(BigDecimal.ZERO), 1L))
                .isInstanceOf(ConflictoException.class);
        verify(cajas).insertarAbierta(1L, fecha, BigDecimal.ZERO);
        verifyNoMoreInteractions(cajas);
    }
}
