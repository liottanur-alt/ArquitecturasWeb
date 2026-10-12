
package Servicios.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entidad, Object id) {
        super("No se encontró " + entidad + " con identificador " + id);
    }
}
