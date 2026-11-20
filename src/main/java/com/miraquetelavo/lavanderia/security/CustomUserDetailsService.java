package com.miraquetelavo.lavanderia.security;

import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public CustomUserDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Cuenta loadUserByUsername(String username) {
        var usuario = usuarios.findByUsernameNormalizado(Usuario.normalizarUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
        return new Cuenta(usuario);
    }

    public JwtAuthenticationToken autenticar(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Token inválido.");
        }
        var usuario = usuarios.findByIdAndActivoTrue(id)
                .filter(cuenta -> cuenta.getVersionCredenciales().toString().equals(jwt.getClaimAsString("ver")))
                .orElseThrow(() -> new BadCredentialsException("Token inválido."));
        return new JwtAuthenticationToken(jwt, List.of(autoridad(usuario)));
    }

    private static SimpleGrantedAuthority autoridad(Usuario usuario) {
        return new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre().name());
    }

    public static final class Cuenta extends User {

        private final Long id;
        private final String versionCredenciales;

        private Cuenta(Usuario usuario) {
            super(usuario.getUsername(), usuario.getPassword(), usuario.isActivo(), true, true, true,
                    List.of(autoridad(usuario)));
            this.id = usuario.getId();
            this.versionCredenciales = usuario.getVersionCredenciales().toString();
        }

        public Long id() {
            return id;
        }

        public String versionCredenciales() {
            return versionCredenciales;
        }
    }
}
