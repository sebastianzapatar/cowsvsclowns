package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Short version of an owner, for when it appears inside a cow.
 * Same reason as {@code CowSummaryResponse}: avoiding infinite JSON.
 */
@Schema(description = "Minimal owner data, used inside other resources")
public record OwnerSummaryResponse(

        @Schema(description = "Owner identifier", example = "1")
        Long id,

        @Schema(description = "Name and last name", example = "Sebastián Zapata")
        String fullName
) {
}
