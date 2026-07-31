package com.sebasmalparqueado.cowsvsclown.cows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Datos para crear una vaca. Resuelve las dos relaciones de una sola vez:
 *
 * <ul>
 *   <li>{@code ownerId} es la inserción <b>1 a N</b>: obligatorio, porque la
 *       columna owner_id de la tabla "cows" es NOT NULL.</li>
 *   <li>{@code clownIds} es la inserción <b>N a M</b>: opcional, cada id crea
 *       una fila en la tabla intermedia clown_cow.</li>
 * </ul>
 */
@Schema(description = "Datos para crear una vaca, con su dueño y sus payasos")
public record CowRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre de la vaca (no se puede repetir)", example = "Lola")
        String name,

        @Min(value = 1, message = "el peso debe ser mayor que 0")
        @Schema(description = "Peso en kilogramos", example = "450")
        int weight,

        @Min(value = 0, message = "la producción de leche no puede ser negativa")
        @Schema(description = "Litros de leche por día", example = "12")
        int milkperday,

        @NotNull(message = "la vaca tiene que tener un dueño")
        @Schema(description = "Id del dueño al que pertenece la vaca (relación 1 a N)",
                example = "1")
        Long ownerId,

        @Schema(description = "Ids de los payasos a los que se asigna la vaca "
                + "(relación N a M). Opcional.")
        List<UUID> clownIds
) {

    /** Devuelve los ids de payasos nunca en null, para no chequearlo en el servicio. */
    public List<UUID> clownIdsOrEmpty() {
        return clownIds == null ? List.of() : clownIds;
    }
}
