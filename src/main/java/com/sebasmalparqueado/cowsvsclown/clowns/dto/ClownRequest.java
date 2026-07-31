package com.sebasmalparqueado.cowsvsclown.clowns.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Datos para crear un payaso.
 *
 * <p>{@code cowIds} es la inserción <b>N a M</b> desde el lado del payaso: cada
 * id que venga acá se convierte en una fila de la tabla intermedia clown_cow.
 * Las vacas tienen que existir y estar activas.</p>
 */
@Schema(description = "Datos para crear un payaso, con las vacas que se le asignan")
public record ClownRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre del payaso (no se puede repetir)", example = "Pennywise")
        String name,

        @Size(max = 255, message = "la descripción no puede pasar de 255 caracteres")
        @Schema(description = "Descripción del payaso", example = "Vive en la alcantarilla")
        String description,

        @Schema(description = "Ids de las vacas que se le asignan (relación N a M). Opcional.")
        List<UUID> cowIds
) {

    /** Devuelve los ids de vacas nunca en null, para no chequearlo en el servicio. */
    public List<UUID> cowIdsOrEmpty() {
        return cowIds == null ? List.of() : cowIds;
    }
}
