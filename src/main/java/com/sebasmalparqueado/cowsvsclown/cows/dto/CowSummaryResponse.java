package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Versión corta de una vaca, para cuando aparece dentro de otro recurso
 * (la lista de vacas de un dueño o de un payaso).
 *
 * <p>Existe para cortar la recursión: si ahí se devolviera {@code CowResponse}
 * completo, ese traería su dueño y sus payasos, que a su vez traerían sus
 * vacas... y el JSON no terminaría nunca.</p>
 */
@Schema(description = "Datos mínimos de una vaca, usados dentro de otros recursos")
public record CowSummaryResponse(

        @Schema(description = "Identificador de la vaca")
        UUID id,

        @Schema(description = "Nombre de la vaca", example = "Lola")
        String name,

        @Schema(description = "Litros de leche por día", example = "12")
        int milkperday
) {
}
