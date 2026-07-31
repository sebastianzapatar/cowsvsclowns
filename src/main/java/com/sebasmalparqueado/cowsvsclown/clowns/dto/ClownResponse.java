package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Lo que la API devuelve de un payaso, con las vacas que tiene asignadas
 * (el otro extremo de la relación N a M).
 */
@Schema(description = "Payaso con las vacas que tiene asignadas")
public record ClownResponse(

        @Schema(description = "Identificador del payaso")
        UUID id,

        @Schema(description = "Nombre del payaso", example = "Pennywise")
        String name,

        @Schema(description = "Descripción", example = "Vive en la alcantarilla")
        String description,

        @Schema(description = "false si el payaso fue dado de baja lógicamente")
        boolean active,

        @Schema(description = "Cuántas vacas activas tiene asignadas", example = "2")
        int totalCows,

        @Schema(description = "Vacas activas asignadas")
        List<CowSummaryResponse> cows
) {
}
