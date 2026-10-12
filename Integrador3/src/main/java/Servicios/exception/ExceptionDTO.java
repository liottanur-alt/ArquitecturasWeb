package Servicios.exception;

import java.time.LocalDateTime;

public class ExceptionDTO {

    private String mensaje;
    private int status;
    private String error;
    private String path;
    private LocalDateTime timestamp;

    public ExceptionDTO() {
        this.timestamp = LocalDateTime.now();
    }

    public ExceptionDTO(String mensaje, int status, String error, String path) {
        this();
        this.mensaje = mensaje;
        this.status = status;
        this.error = error;
        this.path = path;
    }

    // Getters y Setters
    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}