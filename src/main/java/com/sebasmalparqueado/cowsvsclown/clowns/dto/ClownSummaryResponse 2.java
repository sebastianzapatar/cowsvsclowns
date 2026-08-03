package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Short version of a clown, used when it appears inside a cow.
 * Same reason as {@code CowSummaryResponse}: to avoid infinite JSON recursion.
 */
@Schema(description = "Minimal clown data, used within other resources")
public record ClownSummaryResponse(

        @Schema(description = "Clown identifier")
        UUID id,

        @Schema(description = "Clown name", example = "Pennywise")
        String name
) {
}
