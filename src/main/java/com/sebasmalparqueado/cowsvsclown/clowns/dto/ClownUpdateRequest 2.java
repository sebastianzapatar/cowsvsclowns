package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Data to modify a clown via PATCH. Null fields mean "do not touch";
 * see full explanation in {@code CowUpdateRequest}.
 *
 * <p>Assigned cows are not changed here: for that use the endpoints
 * {@code POST/DELETE /api/clowns/{id}/cows/{cowId}}.</p>
 */
@Schema(description = "Clown fields to modify. Fields not sent are left unchanged.")
public record ClownUpdateRequest(

        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "New name", example = "Pennywise the dancing clown")
        String name,

        @Size(max = 255, message = "description cannot exceed 255 characters")
        @Schema(description = "New description", example = "Now works at the circus")
        String description
) {

    /** True if the request has no fields: there is nothing to update. */
    public boolean isEmpty() {
        return name == null && description == null;
    }
}
