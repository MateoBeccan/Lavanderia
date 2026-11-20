package com.miraquetelavo.lavanderia.rol.service;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.entity.Rol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolService {

    private final RolRepository repository;

    public RolService(RolRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void inicializar() {
        repository.inicializar();
    }

    @Transactional(readOnly = true)
    public Rol obtener(CodigoRol codigo) {
        return repository.findByNombre(codigo)
                .orElseThrow(() -> new IllegalStateException("Falta un rol del catálogo."));
    }

    // Todas las escrituras administrativas toman este bloqueo antes de leer usuarios.
    @Transactional(propagation = Propagation.MANDATORY)
    public Rol bloquearAdministracion() {
        return repository.findOneByNombre(CodigoRol.ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException("Falta el rol administrador."));
    }
}
