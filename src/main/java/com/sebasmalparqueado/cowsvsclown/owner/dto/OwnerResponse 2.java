package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What the API returns for an owner. Includes the names of their active cows:
 * this is the "N" side of the 1 to N relationship seen from the "1".
 */
@Schema(description = "Owner with their cows")
public record OwnerResponse(

        @Schema(description = "Owner identifier", example = "1")
        Long id,

        @Schema(description = "Name", example = "Sebastián")
        String firstName,

        @Schema(description = "Last name", example = "Zapata")
        String lastName,

        @Schema(description = "Name and last name together", example = "Sebastián Zapata")
        String fullName,

        @Schema(description = "false if the owner was logically deleted")
        boolean active,

        @Schema(description = "How many active cows they have", example = "3")
        int totalCows,

        @Schema(description = "Names of their active cows", example = "[\"Lola\", \"Margarita\"]")
        List<String> cows
) {
}
