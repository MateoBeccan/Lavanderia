package com.miraquetelavo.lavanderia.usuario.entity;

import com.miraquetelavo.lavanderia.rol.entity.Rol;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GeneratedColumn;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Getter
@DynamicUpdate
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(name = Usuario.UK_USERNAME, columnNames = "username_normalizado"),
        @UniqueConstraint(name = Usuario.UK_EMAIL, columnNames = "email_normalizado")
})
public class Usuario {

    public static final String UK_USERNAME = "uk_usuarios_username";
    public static final String UK_EMAIL = "uk_usuarios_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false, foreignKey = @ForeignKey(name = "fk_usuarios_rol"))
    private Rol rol;

    @Column(nullable = false, length = 50)
    private String username;

    @GeneratedColumn("lower(username)")
    @Column(name = "username_normalizado", nullable = false, insertable = false, updatable = false,
            columnDefinition = "text", options = "not null")
    private String usernameNormalizado;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String email;

    @GeneratedColumn("lower(email)")
    @Column(name = "email_normalizado", nullable = false, insertable = false, updatable = false,
            columnDefinition = "text", options = "not null")
    private String emailNormalizado;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

    private LocalDateTime ultimoAcceso;

    @Column(nullable = false)
    private UUID versionCredenciales;

    protected Usuario() {
    }

    public Usuario(String username, String passwordHash, String nombre, String email, Rol rol) {
        actualizar(username, nombre, email, rol);
        cambiarPassword(passwordHash);
    }

    public void actualizar(String username, String nombre, String email, Rol rol) {
        this.username = normalizarUsername(username);
        this.nombre = nombre.strip();
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.rol = rol;
    }

    public static String normalizarUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public void cambiarPassword(String passwordHash) {
        this.password = passwordHash;
        versionCredenciales = UUID.randomUUID();
    }

    public void desactivar() {
        activo = false;
        versionCredenciales = UUID.randomUUID();
    }

    public void activar() {
        activo = true;
    }
}
