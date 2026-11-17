package org.msvcompraentradasol.controllers;

import feign.FeignException;
import feign.RetryableException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        return build(exception.getStatusCode(), exception.getReason(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String detalle = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", detalle);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", exception.getMessage());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleUnreadable(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        return build(
                HttpStatus.CONFLICT,
                "Conflicto de datos",
                "La operación viola una restricción de la base de datos"
        );
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(RetryableException exception) {
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo conectar con un servicio requerido",
                null
        );
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleRemoteServiceError(FeignException exception) {
        int remoteStatus = exception.status();
        if (remoteStatus >= 400 && remoteStatus < 500) {
            return build(
                    HttpStatusCode.valueOf(remoteStatus),
                    "El servicio requerido rechazó la operación",
                    null
            );
        }
        return build(
                HttpStatus.BAD_GATEWAY,
                "Un servicio requerido devolvió un error",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatusCode status,
            String error,
            String detalle
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                error,
                detalle,
                MDC.get("correlationId"),
                Instant.now()
        ));
    }

    public record ApiError(
            String error,
            String detalle,
            String correlationId,
            Instant timestamp
    ) {
    }
}
