package com.sebasmalparqueado.cowsvsclown.cows.dto;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Lo que la API devuelve de una vaca: sus datos propios más las dos relaciones
 * ya resueltas, el dueño (1 a N) y los payasos (N a M), ambos en versión corta.
 */
@Schema(description = "Vaca con su dueño y sus payasos")
public record CowResponse(

        @Schema(description = "Identificador de la vaca")
        UUID id,

        @Schema(description = "Nombre de la vaca", example = "Lola")
        String name,

        @Schema(description = "Peso en kilogramos", example = "450")
        int weight,

        @Schema(description = "Litros de leche por día", example = "12")
        int milkperday,

        @Schema(description = "false si la vaca fue dada de baja lógicamente")
        boolean active,

        @Schema(description = "Dueño de la vaca (lado 1 de la relación 1 a N)")
        OwnerSummaryResponse owner,

        @Schema(description = "Payasos asignados a esta vaca (relación N a M)")
        List<ClownSummaryResponse> clowns
) {
}
