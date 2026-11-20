package com.miraquetelavo.lavanderia.security;

import com.miraquetelavo.lavanderia.auth.dto.LoginResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class JwtService {

    private static final String ISSUER = "mira-que-te-lavo";
    private final NimbusJwtDecoder decoder;
    private final JwtEncoder encoder;
    private final long segundos;

    public JwtService(@Value("${lavanderia.jwt.secret:}") String secreto,
                      @Value("${lavanderia.jwt.ttl-seconds:3600}") long segundos) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("LAVANDERIA_JWT_SECRET debe estar en Base64.");
        }
        if (bytes.length < 32 || segundos <= 0) {
            throw new IllegalStateException("JWT requiere una clave Base64 de al menos 32 bytes y una vigencia positiva.");
        }
        this.segundos = segundos;
        var clave = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(ISSUER),
                new JwtTimestampValidator(Duration.ZERO),
                new JwtClaimValidator<Instant>("exp", exp -> exp != null && exp.isAfter(Instant.now()))));
    }

    public LoginResponse emitir(Long id, String versionCredenciales) {
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(id.toString()).issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(segundos)).claim("ver", versionCredenciales).build();
        var encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(encabezado, claims));
        return new LoginResponse(token.getTokenValue(), "Bearer", segundos);
    }

    public JwtDecoder decoder() {
        return decoder;
    }
}
