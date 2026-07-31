package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * The request was well-formed but violates business rules. It translates to a 400.
 *
 * <p>Used for rules that validation annotations cannot express because they
 * depend on database state. Example: "cannot assign an inactive cow to a clown".</p>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
