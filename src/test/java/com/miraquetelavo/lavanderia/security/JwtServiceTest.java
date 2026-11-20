package com.miraquetelavo.lavanderia.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final byte[] clave = claveNueva();
    private final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(clave), 3600);

    @Test
    void verificaElTokenEmitido() {
        var emitido = jwt.emitir(42L, "version");
        assertThat(emitido.expiresIn()).isEqualTo(3600);
        var token = jwt.decoder().decode(emitido.accessToken());
        assertThat(token.getSubject()).isEqualTo("42");
        assertThat(token.getClaimAsString("ver")).isEqualTo("version");
    }

    @Test
    void rechazaUnaFirmaIncorrecta() {
        var otroEmisor = new JwtService(Base64.getEncoder().encodeToString(claveNueva()), 3600);
        assertThatThrownBy(() -> jwt.decoder().decode(otroEmisor.emitir(42L, "version").accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenVencidoAunqueLaFirmaSeaValida() throws JOSEException {
        var claims = new JWTClaimsSet.Builder().issuer("mira-que-te-lavo").subject("42").claim("ver", "version")
                .expirationTime(Date.from(Instant.now().minusSeconds(1))).build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(clave));
        assertThatThrownBy(() -> jwt.decoder().decode(token.serialize())).isInstanceOf(JwtException.class);
    }

    private static byte[] claveNueva() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
