package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Lo que la API devuelve de un dueño. Incluye los nombres de sus vacas activas:
 * este es el lado "N" de la relación 1 a N visto desde el "1".
 */
@Schema(description = "Dueño con sus vacas")
public record OwnerResponse(

        @Schema(description = "Identificador del dueño", example = "1")
        Long id,

        @Schema(description = "Nombre", example = "Sebastián")
        String firstName,

        @Schema(description = "Apellido", example = "Zapata")
        String lastName,

        @Schema(description = "Nombre y apellido juntos", example = "Sebastián Zapata")
        String fullName,

        @Schema(description = "false si el dueño fue dado de baja lógicamente")
        boolean active,

        @Schema(description = "Cuántas vacas activas tiene", example = "3")
        int totalCows,

        @Schema(description = "Nombres de sus vacas activas", example = "[\"Lola\", \"Margarita\"]")
        List<String> cows
) {
}
