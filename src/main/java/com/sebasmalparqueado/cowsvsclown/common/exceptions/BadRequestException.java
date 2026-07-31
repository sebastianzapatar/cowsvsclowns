package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * La petición llegó bien formada pero pide algo que no tiene sentido según las
 * reglas del negocio. Se traduce a un 400.
 *
 * <p>Es para reglas que las anotaciones de validación no pueden expresar,
 * porque dependen del estado de la base. Ejemplo: "no se puede asignar una
 * vaca inactiva a un payaso".</p>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
