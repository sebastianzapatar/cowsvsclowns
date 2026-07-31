package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Datos para modificar un dueño con PATCH. Los campos en null significan
 * "no lo toques"; ver la explicación completa en {@code CowUpdateRequest}.
 *
 * <p>Las vacas no se cambian acá: se crean con {@code POST /api/cows} y se
 * traspasan con {@code PATCH /api/cows/{id}/owner/{ownerId}}.</p>
 */
@Schema(description = "Campos a modificar de un dueño. Los que no se envían se dejan igual.")
public record OwnerUpdateRequest(

        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nuevo nombre", example = "Sebastián")
        String firstName,

        @Size(min = 2, max = 100, message = "el apellido debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nuevo apellido", example = "Zapata")
        String lastName
) {

    /** True si la petición no trae ningún campo: no habría nada que actualizar. */
    public boolean isEmpty() {
        return firstName == null && lastName == null;
    }
}
