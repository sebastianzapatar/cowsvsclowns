package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        String message,
        int status,
        LocalDateTime localDateTime,
        Map<String, String> validationErrors//solo para validaciones
        //de campos
) {
    public ErrorResponse(String message, int status,
                         LocalDateTime localDateTime) {
        this( message, status, localDateTime, null);
    }
}
