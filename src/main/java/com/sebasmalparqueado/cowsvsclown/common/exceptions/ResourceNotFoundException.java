package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * El recurso pedido no existe (o está dado de baja). Se traduce a un 404.
 *
 * <p>Es RuntimeException a propósito: al no ser chequeada no obliga a llenar
 * las firmas de los servicios con {@code throws}, y Spring hace rollback
 * automático de la transacción cuando se lanza.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Atajo para el mensaje típico: "No existe Cow con id X".
     *
     * @param resource nombre de la entidad, por ejemplo "Cow"
     * @param id       identificador que se buscó
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(
                "No existe %s con id %s".formatted(resource, id));
    }
}
