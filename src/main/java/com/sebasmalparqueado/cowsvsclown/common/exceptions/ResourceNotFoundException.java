package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * The requested resource does not exist (or has been logically deleted). Translates to a 404.
 *
 * <p>It is intentionally a RuntimeException: by being unchecked, it does not force filling
 * service signatures with {@code throws}, and Spring automatically rolls back
 * the transaction when it is thrown.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Shortcut for the typical message: "There is no Cow with id X".
     *
     * @param resource entity name, e.g., "Cow"
     * @param id       identifier that was searched
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(
                "There is no %s with id %s".formatted(resource, id));
    }
}
