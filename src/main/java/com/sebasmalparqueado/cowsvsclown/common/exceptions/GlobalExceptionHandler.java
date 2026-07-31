package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central translator of exceptions into HTTP responses.
 *
 * <p>Thanks to this {@code @RestControllerAdvice}, controllers don't need a
 * single try/catch: they throw the corresponding business exception, and here
 * the status code and body are decided. All responses are formatted as
 * {@link ErrorResponse}, so the client always receives the same structure.</p>
 *
 * <p>Spring chooses the most specific handler that applies, which is why
 * {@code handleUnexpected(Exception)} at the end is only triggered when no other
 * handler matches.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ======================= Custom Exceptions ==========================

    /** 404: requested resource is not in the database or was logically deleted. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException e, HttpServletRequest request) {

        log.warn("404 in {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    /** 400: business rule violated. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException e, HttpServletRequest request) {

        log.warn("400 in {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** 409: conflicts with current state (duplicates, repeated assignments). */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException e, HttpServletRequest request) {

        log.warn("409 in {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    // ==================== Request Validation =======================

    /**
     * 400: failed validation for a {@code @Valid @RequestBody}.
     *
     * <p>Returns the map field -> message. The field name is extracted from
     * {@code getField()}; errors that are not field-specific (class-level
     * validations) are grouped under the key "object".</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().forEach(fieldError ->
                // merge and not put: if a field has two broken rules, the messages
                // are concatenated instead of losing one.
                errors.merge(
                        fieldError.getField(),
                        String.valueOf(fieldError.getDefaultMessage()),
                        (oldVal, newVal) -> oldVal + "; " + newVal));

        e.getBindingResult().getGlobalErrors().forEach(globalError ->
                errors.merge(
                        "object",
                        String.valueOf(globalError.getDefaultMessage()),
                        (oldVal, newVal) -> oldVal + "; " + newVal));

        log.warn("Validation failed in {}: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        "There are invalid fields in the request",
                        request.getRequestURI(),
                        errors));
    }

    /**
     * 400: failed validation for a single parameter (@PathVariable or
     * @RequestParam annotated with @Min, @NotBlank, etc.).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(violation ->
                errors.put(
                        String.valueOf(violation.getPropertyPath()),
                        violation.getMessage()));

        log.warn("Invalid parameters in {}: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        "There are invalid parameters in the request",
                        request.getRequestURI(),
                        errors));
    }

    /**
     * 400: the body JSON is malformed or a value cannot be converted to the
     * expected type (e.g., {@code "weight": "much"} in an int field).
     *
     * <p>{@code e.getMessage()} is not returned because it includes the Java class
     * and the exact parser position, which is noise for the client.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        log.warn("Unreadable body in {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "The request body is not valid JSON or a field has an unexpected type",
                request);
    }

    /**
     * 400: a URL value could not be converted. The typical case here is a
     * malformed UUID in {@code /api/cows/{id}}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        String expectedType = e.getRequiredType() == null
                ? "the expected type"
                : e.getRequiredType().getSimpleName();

        return build(HttpStatus.BAD_REQUEST,
                "The value '%s' is not valid for the parameter '%s' (expected %s)"
                        .formatted(e.getValue(), e.getName(), expectedType),
                request);
    }

    /** 400: missing a required query param. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST,
                "Missing required parameter '%s'".formatted(e.getParameterName()),
                request);
    }

    // ========================= Route Errors ============================

    /** 404: the URL does not correspond to any endpoint. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND,
                "The path %s does not exist in this API".formatted(request.getRequestURI()),
                request);
    }

    /** 405: the route exists but does not accept this HTTP verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "The %s method is not allowed on this path. Allowed: %s"
                        .formatted(e.getMethod(), e.getSupportedHttpMethods()),
                request);
    }

    // ====================== Database Errors ======================

    /**
     * 409: the database rejected the operation due to a constraint (unique, NOT NULL,
     * foreign key).
     *
     * <p>This is a safety net: services already validate these cases before
     * saving, but a concurrent request might sneak in between validation and insert.
     * The full exception is logged and a generic message is sent to the client,
     * because Postgres text exposes table names and constraints.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException e, HttpServletRequest request) {

        log.error("Integrity violation in {}", request.getRequestURI(), e);
        return build(HttpStatus.CONFLICT,
                "The operation violates a database constraint "
                        + "(duplicated data or missing reference)",
                request);
    }

    // ======================== Safety Net ============================

    /**
     * 500: anything unpredicted.
     *
     * <p>Logged with full stack trace, and a neutral message is returned to the client:
     * filtering here prevents a NullPointerException from exposing internal class paths
     * to the user.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e, HttpServletRequest request) {

        log.error("Unhandled error in {}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred",
                request);
    }

    // =========================== Utility ===============================

    /** Builds the response so no handler repeats the same block. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String message,
                                                HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
