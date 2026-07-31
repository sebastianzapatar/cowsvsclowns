package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Versión corta de un payaso, para cuando aparece dentro de una vaca.
 * Misma razón que {@code CowSummaryResponse}: evitar el JSON infinito.
 */
@Schema(description = "Datos mínimos de un payaso, usados dentro de otros recursos")
public record ClownSummaryResponse(

        @Schema(description = "Identificador del payaso")
        UUID id,

        @Schema(description = "Nombre del payaso", example = "Pennywise")
        String name
) {
}
