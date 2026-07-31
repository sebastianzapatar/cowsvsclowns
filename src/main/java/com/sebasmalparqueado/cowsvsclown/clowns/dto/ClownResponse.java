package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * What the API returns for a clown, with the cows assigned to it
 * (the other end of the N to M relationship).
 */
@Schema(description = "Clown with the cows assigned to it")
public record ClownResponse(

        @Schema(description = "Clown identifier")
        UUID id,

        @Schema(description = "Clown name", example = "Pennywise")
        String name,

        @Schema(description = "Description", example = "Lives in the sewer")
        String description,

        @Schema(description = "false if the clown was logically deleted")
        boolean active,

        @Schema(description = "How many active cows are assigned to it", example = "2")
        int totalCows,

        @Schema(description = "Assigned active cows")
        List<CowSummaryResponse> cows
) {
}
