package com.miraquetelavo.lavanderia.common.exception;

import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;

public final class RestriccionesPostgresql {

    private RestriccionesPostgresql() {
    }

    public static boolean esViolacionUnica(DataIntegrityViolationException ex, String restriccion) {
        return ex.getMostSpecificCause() instanceof PSQLException error
                && PSQLState.UNIQUE_VIOLATION.getState().equals(error.getSQLState())
                && error.getServerErrorMessage() != null
                && restriccion.equals(error.getServerErrorMessage().getConstraint());
    }
}
