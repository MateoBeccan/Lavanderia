package com.miraquetelavo.lavanderia.rol.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_roles_nombre", columnNames = "nombre_rol"),
        check = @CheckConstraint(name = "ck_roles_nombre", constraint = "nombre_rol in ('ADMINISTRADOR', 'EMPLEADO')"))
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "nombre_rol", nullable = false, length = 30)
    private CodigoRol nombre;

    @Column(length = 250)
    private String descripcion;

    protected Rol() {
    }
}
