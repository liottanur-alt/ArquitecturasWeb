package org.msvcompraentradasol.controllers;

import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(RetryableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("No se pudo conectar con un servicio requerido"));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleRemoteServiceError(FeignException exception) {
        int remoteStatus = exception.status();
        if (remoteStatus >= 400 && remoteStatus < 500) {
            HttpStatusCode status = HttpStatusCode.valueOf(remoteStatus);
            return ResponseEntity.status(status)
                    .body(new ApiError("El servicio requerido rechazó la operación"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("Un servicio requerido devolvió un error"));
    }

    public record ApiError(String error) {
    }
}
