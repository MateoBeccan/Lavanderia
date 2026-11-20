package com.miraquetelavo.lavanderia.common.handler;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.common.idempotencia.ConflictoIdempotencia;
import com.miraquetelavo.lavanderia.movimientocaja.service.JornadaCajaCambiadaException;
import org.springframework.dao.ConcurrencyFailureException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail noAutenticado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales o token inválidos.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail sinPermiso() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tiene permiso para esta operación.");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail recursoNoEncontrado(RecursoNoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictoIdempotencia.class)
    public ResponseEntity<ProblemDetail> conflictoIdempotencia(ConflictoIdempotencia ex) {
        boolean enCurso = ex.motivo() == ConflictoIdempotencia.Motivo.EN_CURSO;
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, enCurso
                ? "La operación con esta clave está en curso. Reintente con la misma clave."
                : "La clave ya se utilizó con otra operación o contenido.");
        problema.setProperty("codigo", enCurso ? "IDEMPOTENCIA_EN_CURSO" : "IDEMPOTENCIA_CLAVE_REUTILIZADA");
        var respuesta = ResponseEntity.status(HttpStatus.CONFLICT);
        if (enCurso) {
            respuesta.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return respuesta.body(problema);
    }

    @ExceptionHandler(JornadaCajaCambiadaException.class)
    public ProblemDetail jornadaCajaCambiada(JornadaCajaCambiadaException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("codigo", "CAJA_JORNADA_CAMBIADA");
        return problema;
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail solicitudInvalida(SolicitudInvalidaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail conflictoConcurrente() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La operación encontró un conflicto concurrente.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problema = ProblemDetail.forStatusAndDetail(status, "Hay campos inválidos en la solicitud.");
        problema.setProperty("errores", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorCampo(error.getField(), error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problema = ProblemDetail.forStatusAndDetail(status, "Se requiere un cuerpo JSON válido.");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail restriccionInvalida(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Hay datos inválidos en la solicitud.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail errorInesperado(Exception ex) {
        LOG.error("Error inesperado procesando la solicitud", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado al procesar la solicitud.");
    }

    private record ErrorCampo(String campo, String mensaje) {
    }
}
