package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Data to modify an owner with PATCH. Fields in null mean
 * "do not touch"; see the full explanation in {@code CowUpdateRequest}.
 *
 * <p>Cows are not changed here: they are created with {@code POST /api/cows} and
 * transferred with {@code PATCH /api/cows/{id}/owner/{ownerId}}.</p>
 */
@Schema(description = "Fields to modify of an owner. Those not sent are left unchanged.")
public record OwnerUpdateRequest(

        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "New name", example = "Sebastián")
        String firstName,

        @Size(min = 2, max = 100, message = "last name must be between 2 and 100 characters")
        @Schema(description = "New last name", example = "Zapata")
        String lastName
) {

    /** True if the request brings no fields: there would be nothing to update. */
    public boolean isEmpty() {
        return firstName == null && lastName == null;
    }
}
