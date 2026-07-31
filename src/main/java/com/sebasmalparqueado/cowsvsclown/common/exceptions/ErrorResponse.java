package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato único de error de toda la API. Que todos los errores salgan con la
 * misma forma es lo que le permite al cliente manejarlos sin adivinar.
 *
 * <p>{@code @JsonInclude(NON_NULL)} hace que {@code validationErrors} no
 * aparezca en el JSON cuando es null, es decir en todos los errores que no son
 * de validación de campos.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta estándar de error de la API")
public record ErrorResponse(

        @Schema(description = "Código HTTP", example = "404")
        int status,

        @Schema(description = "Nombre del código HTTP", example = "Not Found")
        String error,

        @Schema(description = "Explicación del problema",
                example = "No existe Cow con id 3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String message,

        @Schema(description = "Ruta que se invocó", example = "/api/cows/3fa85f64")
        String path,

        @Schema(description = "Momento en que ocurrió el error")
        LocalDateTime timestamp,

        @Schema(description = "Errores por campo. Solo viene en fallos de "
                + "validación; en el resto de errores se omite.")
        Map<String, String> validationErrors
) {

    /** Error normal: sin detalle por campo. */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                LocalDateTime.now(),
                null
        );
    }

    /** Error de validación: agrega el mapa campo -> qué está mal. */
    public static ErrorResponse ofValidation(String message,
                                             String path,
                                             Map<String, String> validationErrors) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                path,
                LocalDateTime.now(),
                validationErrors
        );
    }
}
