package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Data to create a clown.
 *
 * <p>{@code cowIds} is the <b>N to M</b> insertion from the clown's side: each
 * id that comes here is converted into a row in the join table clown_cow.
 * Cows must exist and be active.</p>
 */
@Schema(description = "Data to create a clown, along with the cows assigned to it")
public record ClownRequest(

        @NotBlank(message = "name is mandatory")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "Clown's name (must be unique)", example = "Pennywise")
        String name,

        @Size(max = 255, message = "description cannot exceed 255 characters")
        @Schema(description = "Clown's description", example = "Lives in the sewer")
        String description,

        @Schema(description = "Ids of the cows assigned to it (N to M relationship). Optional.")
        List<UUID> cowIds
) {

    /** Returns cow ids, never null, to avoid checking it in the service. */
    public List<UUID> cowIdsOrEmpty() {
        return cowIds == null ? List.of() : cowIds;
    }
}
