package com.sebasmalparqueado.cowsvsclown.common.exceptions;

/**
 * La petición choca con el estado actual de los datos. Se traduce a un 409.
 *
 * <p>La diferencia con {@link BadRequestException}: acá la petición es válida,
 * el problema es que el recurso ya existe o ya está en ese estado. Ejemplos:
 * crear una vaca con un nombre repetido, o asignar dos veces la misma vaca al
 * mismo payaso.</p>
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
