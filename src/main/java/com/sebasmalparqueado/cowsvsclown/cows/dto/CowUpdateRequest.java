package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Datos para modificar una vaca con PATCH.
 *
 * <p>Los tipos son objeto ({@code Integer}, no {@code int}) justamente para
 * poder distinguir "no mandaron el campo" (null) de "mandaron un 0". Con
 * {@code int} el ausente llegaría como 0 y borraría el valor que ya estaba,
 * que es la diferencia entre un PATCH y un PUT.</p>
 *
 * <p>Por eso tampoco hay {@code @NotNull}: cada campo se valida solo si viene.
 * El dueño y los payasos no se cambian acá, tienen sus propios endpoints.</p>
 */
@Schema(description = "Campos a modificar de una vaca. Los que no se envían se dejan igual.")
public record CowUpdateRequest(

        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nuevo nombre", example = "Lola II")
        String name,

        @Min(value = 1, message = "el peso debe ser mayor que 0")
        @Schema(description = "Nuevo peso en kilogramos", example = "470")
        Integer weight,

        @Min(value = 0, message = "la producción de leche no puede ser negativa")
        @Schema(description = "Nueva producción de leche por día", example = "15")
        Integer milkperday
) {

    /** True si la petición no trae ningún campo: no habría nada que actualizar. */
    public boolean isEmpty() {
        return name == null && weight == null && milkperday == null;
    }
}
