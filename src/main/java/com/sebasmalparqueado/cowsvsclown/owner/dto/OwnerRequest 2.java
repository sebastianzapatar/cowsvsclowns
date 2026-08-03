package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Data arriving to create or update an owner.
 *
 * <p>Optionally it can bring cows: if they come, they are created together with the owner
 * (cascading 1 to N insertion). If the field arrives empty or null, the owner is
 * created alone and cows are added to it later.</p>
 */
@Schema(description = "Data to create or update an owner")
public record OwnerRequest(

        @NotBlank(message = "name is mandatory")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "Name", example = "Sebastián")
        String firstName,

        @NotBlank(message = "last name is mandatory")
        @Size(min = 2, max = 100, message = "last name must be between 2 and 100 characters")
        @Schema(description = "Last name", example = "Zapata")
        String lastName,

        /*
         * Cascading @Valid: without this annotation Spring validates the owner but DOES NOT
         * check the rules of each cow in the list, and cows with empty names
         * or negative weight would slip through.
         */
        @Valid
        @Schema(description = "Cows that are created together with the owner (optional)")
        List<OwnerCowRequest> cows
) {

    /** Returns the list of cows never as null, to avoid checking it in the service. */
    public List<OwnerCowRequest> cowsOrEmpty() {
        return cows == null ? List.of() : cows;
    }
}
