package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * Thrown when an operation breaks a business rule (e.g., duplicated name)
 * or when trying to link entities that cannot be linked. Translates to a 409 Conflict.
 *
 * <p>It is unchecked for the same reasons as {@link ResourceNotFoundException}.</p>
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
