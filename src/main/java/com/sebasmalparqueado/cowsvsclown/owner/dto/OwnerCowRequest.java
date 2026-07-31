package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Vaca que se crea <b>en la misma petición</b> que su dueño.
 *
 * <p>No lleva ownerId: el dueño es justamente el que se está creando. Es la
 * forma más directa de la inserción 1 a N, porque el cascade = ALL del
 * {@code @OneToMany} guarda dueño y vacas en una sola transacción.</p>
 */
@Schema(description = "Vaca creada junto con su dueño en una sola petición")
public record OwnerCowRequest(

        @NotBlank(message = "el nombre de la vaca es obligatorio")
        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre de la vaca", example = "Lola")
        String name,

        @Min(value = 1, message = "el peso debe ser mayor que 0")
        @Schema(description = "Peso en kilogramos", example = "450")
        int weight,

        @Min(value = 0, message = "la producción de leche no puede ser negativa")
        @Schema(description = "Litros de leche por día", example = "12")
        int milkperday
) {
}
