package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Data to modify a cow with PATCH.
 *
 * <p>The types are objects ({@code Integer}, not {@code int}) precisely to
 * distinguish "they didn't send the field" (null) from "they sent a 0". With
 * {@code int} the missing field would arrive as 0 and overwrite the existing value,
 * which is the difference between a PATCH and a PUT.</p>
 *
 * <p>That's also why there is no {@code @NotNull}: each field is validated only if it comes.
 * The owner and clowns are not changed here, they have their own endpoints.</p>
 */
@Schema(description = "Fields to modify of a cow. Those not sent are left unchanged.")
public record CowUpdateRequest(

        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "New name", example = "Lola II")
        String name,

        @Min(value = 1, message = "weight must be greater than 0")
        @Schema(description = "New weight in kilograms", example = "470")
        Integer weight,

        @Min(value = 0, message = "milk production cannot be negative")
        @Schema(description = "New milk production per day", example = "15")
        Integer milkperday
) {

    /** True if the request brings no fields: there would be nothing to update. */
    public boolean isEmpty() {
        return name == null && weight == null && milkperday == null;
    }
}
