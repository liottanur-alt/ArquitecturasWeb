package msvevento.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class MensajesDeError extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> atributos = super.getErrorAttributes(request, options);
        int estado = (int) atributos.get("status");
        Throwable error = getError(request);
        String mensaje = switch (estado) {
            case 400 -> "Revisá los datos de la solicitud y volvé a intentarlo.";
            case 401 -> "Iniciá sesión para continuar. Si ya ingresaste, tu sesión puede haber vencido.";
            case 403 -> "Tu cuenta no tiene permiso para realizar esta operación.";
            case 404 -> "No encontramos el recurso solicitado.";
            case 405 -> "Esta operación no está disponible en la dirección solicitada.";
            case 409 -> "La operación no se puede completar con el estado actual de los datos.";
            case 415 -> "El formato de los datos enviados no es compatible.";
            case 502, 503, 504 -> "El servicio no está disponible en este momento. Intentá nuevamente en unos instantes.";
            default -> "No pudimos completar la operación. Si el problema continúa, contactá a soporte.";
        };
        if (error instanceof ResponseStatusException respuesta && respuesta.getReason() != null && estado != 500) {
            mensaje = respuesta.getReason();
        } else if (error instanceof HttpMessageNotReadableException) {
            mensaje = "Revisá que los datos enviados tengan el formato JSON esperado.";
        } else if (error instanceof MethodArgumentNotValidException validacion) {
            var campos = validacion.getFieldErrors().stream().map(campo -> campo.getField()).distinct().toList();
            if (!campos.isEmpty()) {
                mensaje = "Revisá estos campos: " + String.join(", ", campos) + ".";
            }
        } else if (error instanceof MissingServletRequestParameterException parametro) {
            mensaje = "Falta el parámetro «" + parametro.getParameterName() + "».";
        } else if (error instanceof MethodArgumentTypeMismatchException parametro) {
            mensaje = "El valor de «" + parametro.getName() + "» tiene un formato incorrecto.";
        }
        atributos.put("message", mensaje);
        return atributos;
    }
}
