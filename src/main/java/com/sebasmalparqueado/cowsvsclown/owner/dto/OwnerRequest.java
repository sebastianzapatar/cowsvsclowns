package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Datos que llegan para crear o actualizar un dueño.
 *
 * <p>Opcionalmente puede traer vacas: si vienen, se crean junto con el dueño
 * (inserción 1 a N en cascada). Si el campo llega vacío o en null, el dueño se
 * crea solo y las vacas se le agregan después.</p>
 */
@Schema(description = "Datos para crear o actualizar un dueño")
public record OwnerRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(min = 2, max = 100, message = "el nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre", example = "Sebastián")
        String firstName,

        @NotBlank(message = "el apellido es obligatorio")
        @Size(min = 2, max = 100, message = "el apellido debe tener entre 2 y 100 caracteres")
        @Schema(description = "Apellido", example = "Zapata")
        String lastName,

        /*
         * @Valid en cascada: sin esta anotación Spring valida el dueño pero NO
         * revisa las reglas de cada vaca de la lista, y se colarían vacas con
         * nombre vacío o peso negativo.
         */
        @Valid
        @Schema(description = "Vacas que se crean junto con el dueño (opcional)")
        List<OwnerCowRequest> cows
) {

    /** Devuelve la lista de vacas nunca en null, para no chequearlo en el servicio. */
    public List<OwnerCowRequest> cowsOrEmpty() {
        return cows == null ? List.of() : cows;
    }
}
