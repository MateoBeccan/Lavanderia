package com.miraquetelavo.lavanderia.cliente.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(name = Cliente.UK_DNI, columnNames = "dni"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente {

    public static final String UK_DNI = "uk_clientes_dni";
    public static final int LONGITUD_NOMBRE = 100;
    public static final int LONGITUD_TELEFONO = 30;
    public static final int LONGITUD_EMAIL = 254;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Column(nullable = false, length = LONGITUD_NOMBRE)
    private String apellido;

    @Column(length = 8)
    private String dni;

    @Column(length = LONGITUD_TELEFONO)
    private String telefono;

    @Column(length = LONGITUD_EMAIL)
    private String email;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Cliente(String nombre, String apellido, String dni, String telefono, String email) {
        actualizarDatos(nombre, apellido, dni, telefono, email);
    }

    public void actualizarDatos(String nombre, String apellido, String dni, String telefono, String email) {
        this.nombre = nombre.strip();
        this.apellido = apellido.strip();
        this.dni = dni;
        this.telefono = telefono == null || telefono.isBlank() ? null : telefono.strip();
        this.email = email == null || email.isBlank() ? null : email;
    }

    public void desactivar() {
        activo = false;
    }
}
