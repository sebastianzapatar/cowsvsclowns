package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Versión corta de un dueño, para cuando aparece dentro de una vaca.
 * Misma razón que {@code CowSummaryResponse}: evitar el JSON infinito.
 */
@Schema(description = "Datos mínimos de un dueño, usados dentro de otros recursos")
public record OwnerSummaryResponse(

        @Schema(description = "Identificador del dueño", example = "1")
        Long id,

        @Schema(description = "Nombre y apellido", example = "Sebastián Zapata")
        String fullName
) {
}
