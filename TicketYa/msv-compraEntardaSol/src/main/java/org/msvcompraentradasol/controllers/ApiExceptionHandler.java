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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
                .map(error -> error.getField() + ": " + mensajeCampo(error))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", detalle);
    }

    private String mensajeCampo(FieldError error) {
        if (error.getCode() == null) {
            return "Revisá el valor ingresado.";
        }
        return switch (error.getCode()) {
            case "NotNull", "NotBlank", "NotEmpty" -> "Este campo es obligatorio.";
            case "Positive" -> "Ingresá un valor mayor que cero.";
            case "Size" -> "El valor tiene una longitud o cantidad fuera del límite permitido.";
            default -> error.getDefaultMessage();
        };
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception) {
        String detalle = exception.getConstraintViolations().stream()
                .map(violacion -> violacion.getMessage())
                .distinct()
                .collect(Collectors.joining(". "));
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "Revisá que el cuerpo de la solicitud sea JSON válido y que los campos tengan el formato esperado.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "Falta el parámetro «" + exception.getParameterName() + "».");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "El valor de «" + exception.getName() + "» tiene un formato incorrecto.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        return build(
                HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes",
                "Revisá si la compra, entrada o solicitud ya fue registrada."
        );
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(RetryableException exception) {
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio no está disponible en este momento. Intentá nuevamente en unos instantes.",
                null
        );
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleRemoteServiceError(FeignException exception) {
        int remoteStatus = exception.status();
        if (remoteStatus >= 400 && remoteStatus < 500) {
            return build(
                    HttpStatusCode.valueOf(remoteStatus),
                    switch (remoteStatus) {
                        case 404 -> "No encontramos uno de los recursos necesarios. Revisá el evento o la reserva.";
                        case 409 -> "La operación ya no se puede completar. Actualizá los datos y volvé a intentarlo.";
                        default -> "No se pudo completar la operación. Revisá los datos y los permisos de tu cuenta.";
                    },
                    null
            );
        }
        return build(
                HttpStatus.BAD_GATEWAY,
                "No pudimos completar la operación. Intentá nuevamente en unos instantes.",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un problema al procesar la solicitud.",
                "Si el problema continúa, contactá a soporte con el identificador de la operación.");
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
