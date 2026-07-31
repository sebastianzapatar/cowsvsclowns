package com.sebasmalparqueado.cowsvsclown.cows.dto;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * What the API returns for a cow: its own data plus the two resolved relationships,
 * the owner (1 to N) and the clowns (N to M), both in short version.
 */
@Schema(description = "Cow with its owner and its clowns")
public record CowResponse(

        @Schema(description = "Cow identifier")
        UUID id,

        @Schema(description = "Cow name", example = "Lola")
        String name,

        @Schema(description = "Weight in kilograms", example = "450")
        int weight,

        @Schema(description = "Liters of milk per day", example = "12")
        int milkperday,

        @Schema(description = "false if the cow was logically deleted")
        boolean active,

        @Schema(description = "Cow owner (side 1 of the 1 to N relationship)")
        OwnerSummaryResponse owner,

        @Schema(description = "Clowns assigned to this cow (N to M relationship)")
        List<ClownSummaryResponse> clowns
) {
}
