package com.universidad.msvsectoerasiento.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    /** Datos invalidos rechazados por la validacion de la capa de servicio. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> invalido(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Revisá los identificadores, las cantidades y los campos obligatorios antes de continuar."));
    }

    /** El cliente puede reintentar con la misma clave si la base no pudo adquirir un bloqueo. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> ocupado(PessimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Estamos actualizando la disponibilidad. Intentá nuevamente con la misma clave de idempotencia."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflicto(DataIntegrityViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El sector, asiento o reserva ya existe o todavía está en uso. Revisá su estado antes de continuar.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        String detalle = null;
        if (exception instanceof HttpMessageNotReadableException) {
            detalle = "Revisá que los datos enviados tengan el formato JSON esperado.";
        } else if (exception instanceof MissingServletRequestParameterException parametro) {
            detalle = "Falta el parámetro «" + parametro.getParameterName() + "».";
        } else if (exception instanceof MethodArgumentTypeMismatchException parametro) {
            detalle = "El valor de «" + parametro.getName() + "» tiene un formato incorrecto.";
        } else if (exception instanceof MethodArgumentNotValidException validacion) {
            detalle = "Revisá estos campos: " + String.join(", ", validacion.getFieldErrors().stream()
                    .map(error -> error.getField()).distinct().toList()) + ".";
        }
        if (detalle != null) {
            body = ProblemDetail.forStatusAndDetail(status, detalle);
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }
}
