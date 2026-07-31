package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Datos para modificar un payaso con PATCH. Los campos en null significan
 * "no lo toques"; ver la explicación completa en {@code CowUpdateRequest}.
 *
 * <p>Las vacas asignadas no se cambian acá: para eso están los endpoints
 * {@code POST/DELETE /api/clowns/{id}/cows/{cowId}}.</p>
 */
@Schema(description = "Campos a modificar de un payaso. Los que no se envían se dejan igual.")
public record ClownUpdateRequest(

        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nuevo nombre", example = "Pennywise el bailarín")
        String name,

        @Size(max = 255, message = "la descripción no puede pasar de 255 caracteres")
        @Schema(description = "Nueva descripción", example = "Ahora sale del circo")
        String description
) {

    /** True si la petición no trae ningún campo: no habría nada que actualizar. */
    public boolean isEmpty() {
        return name == null && description == null;
    }
}
