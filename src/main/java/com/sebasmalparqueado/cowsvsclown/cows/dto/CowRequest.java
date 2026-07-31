package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Data to create a cow. Resolves both relationships at once:
 *
 * <ul>
 *   <li>{@code ownerId} is the <b>1 to N</b> insertion: mandatory, because the
 *       owner_id column in the "cows" table is NOT NULL.</li>
 *   <li>{@code clownIds} is the <b>N to M</b> insertion: optional, each id creates
 *       a row in the clown_cow join table.</li>
 * </ul>
 */
@Schema(description = "Data to create a cow, with its owner and its clowns")
public record CowRequest(

        @NotBlank(message = "name is mandatory")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "Cow's name (must be unique)", example = "Lola")
        String name,

        @Min(value = 1, message = "weight must be greater than 0")
        @Schema(description = "Weight in kilograms", example = "450")
        int weight,

        @Min(value = 0, message = "milk production cannot be negative")
        @Schema(description = "Liters of milk per day", example = "12")
        int milkperday,

        @NotNull(message = "the cow must have an owner")
        @Schema(description = "Id of the owner to whom the cow belongs (1 to N relationship)",
                example = "1")
        Long ownerId,

        @Schema(description = "Ids of the clowns to which the cow is assigned "
                + "(N to M relationship). Optional.")
        List<UUID> clownIds
) {

    /** Returns clown ids, never null, to avoid checking it in the service. */
    public List<UUID> clownIdsOrEmpty() {
        return clownIds == null ? List.of() : clownIds;
    }
}
