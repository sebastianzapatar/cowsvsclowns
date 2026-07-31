package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Short version of a cow, for when it appears inside another resource
 * (the cow list of an owner or a clown).
 *
 * <p>It exists to break the recursion: if the complete {@code CowResponse}
 * was returned there, it would bring its owner and its clowns, which in turn
 * would bring their cows... and the JSON would never end.</p>
 */
@Schema(description = "Minimal cow data, used inside other resources")
public record CowSummaryResponse(

        @Schema(description = "Cow identifier")
        UUID id,

        @Schema(description = "Cow name", example = "Lola")
        String name,

        @Schema(description = "Liters of milk per day", example = "12")
        int milkperday
) {
}
