package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Global API error format. Ensuring all errors have the same structure
 * allows clients to handle them predictably.
 *
 * <p>{@code @JsonInclude(NON_NULL)} hides {@code validationErrors} from
 * the JSON when it is null, which is true for all non-validation errors.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "HTTP status name", example = "Not Found")
        String error,

        @Schema(description = "Explanation of the problem",
                example = "There is no Cow with id 3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String message,

        @Schema(description = "Invoked path", example = "/api/cows/3fa85f64")
        String path,

        @Schema(description = "Timestamp when the error occurred")
        LocalDateTime timestamp,

        @Schema(description = "Field-specific errors. Only present in validation "
                + "failures; omitted in all other errors.")
        Map<String, String> validationErrors
) {

    /** Normal error: without field-specific details. */
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

    /** Validation error: includes the field -> error message map. */
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
