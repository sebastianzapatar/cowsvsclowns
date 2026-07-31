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
 * Traductor central de excepciones a respuestas HTTP.
 *
 * <p>Gracias a este {@code @RestControllerAdvice} los controllers no llevan un
 * solo try/catch: lanzan la excepción de negocio que corresponda y acá se
 * decide el código y el cuerpo. Todas las respuestas salen como
 * {@link ErrorResponse}, así el cliente siempre recibe la misma forma.</p>
 *
 * <p>Spring elige el handler más específico que aplique, por eso el
 * {@code handleUnexpected(Exception)} del final solo entra cuando ningún otro
 * coincide.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ======================= Excepciones propias ==========================

    /** 404: se pidió algo que no está en la base o está dado de baja. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException e, HttpServletRequest request) {

        log.warn("404 en {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    /** 400: regla de negocio incumplida. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException e, HttpServletRequest request) {

        log.warn("400 en {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** 409: choca con el estado actual (duplicados, asignaciones repetidas). */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException e, HttpServletRequest request) {

        log.warn("409 en {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    // ==================== Validación de la petición =======================

    /**
     * 400: falló la validación de un {@code @Valid @RequestBody}.
     *
     * <p>Devuelve el mapa campo -> mensaje. El nombre del campo se saca de
     * {@code getField()}; los errores que no son de campo (validaciones a nivel
     * de la clase completa) se agrupan bajo la clave "object".</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().forEach(fieldError ->
                // merge y no put: si un campo tiene dos reglas rotas se juntan
                // los mensajes en vez de perderse uno.
                errors.merge(
                        fieldError.getField(),
                        String.valueOf(fieldError.getDefaultMessage()),
                        (viejo, nuevo) -> viejo + "; " + nuevo));

        e.getBindingResult().getGlobalErrors().forEach(globalError ->
                errors.merge(
                        "object",
                        String.valueOf(globalError.getDefaultMessage()),
                        (viejo, nuevo) -> viejo + "; " + nuevo));

        log.warn("Validación fallida en {}: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        "Hay campos inválidos en la petición",
                        request.getRequestURI(),
                        errors));
    }

    /**
     * 400: falló la validación de un parámetro suelto (@PathVariable o
     * @RequestParam anotado con @Min, @NotBlank, etc.).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(violation ->
                errors.put(
                        String.valueOf(violation.getPropertyPath()),
                        violation.getMessage()));

        log.warn("Parámetros inválidos en {}: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        "Hay parámetros inválidos en la petición",
                        request.getRequestURI(),
                        errors));
    }

    /**
     * 400: el JSON del body está roto o un valor no se puede convertir al tipo
     * esperado (por ejemplo {@code "weight": "mucho"} en un campo int).
     *
     * <p>No se devuelve {@code e.getMessage()} porque trae la clase Java y la
     * posición exacta del parser: es ruido para el cliente.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        log.warn("Body ilegible en {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o algún campo "
                        + "tiene un tipo que no corresponde",
                request);
    }

    /**
     * 400: un valor de la URL no se pudo convertir. El caso típico acá es un
     * UUID mal escrito en {@code /api/cows/{id}}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        String tipoEsperado = e.getRequiredType() == null
                ? "el tipo esperado"
                : e.getRequiredType().getSimpleName();

        return build(HttpStatus.BAD_REQUEST,
                "El valor '%s' no es válido para el parámetro '%s' (se esperaba %s)"
                        .formatted(e.getValue(), e.getName(), tipoEsperado),
                request);
    }

    /** 400: falta un query param obligatorio. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST,
                "Falta el parámetro obligatorio '%s'".formatted(e.getParameterName()),
                request);
    }

    // ========================= Errores de ruta ============================

    /** 404: la URL no corresponde a ningún endpoint. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND,
                "La ruta %s no existe en esta API".formatted(request.getRequestURI()),
                request);
    }

    /** 405: la ruta existe pero no acepta ese verbo HTTP. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "El método %s no está permitido en esta ruta. Permitidos: %s"
                        .formatted(e.getMethod(), e.getSupportedHttpMethods()),
                request);
    }

    // ====================== Errores de base de datos ======================

    /**
     * 409: la base rechazó la operación por una restricción (único, NOT NULL,
     * llave foránea).
     *
     * <p>Es la red de seguridad: los servicios ya validan estos casos antes de
     * guardar, pero entre la validación y el insert puede colarse otra petición
     * concurrente. Se loguea completo y al cliente se le manda un mensaje
     * genérico, porque el texto de Postgres expone nombres de tablas y
     * constraints.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException e, HttpServletRequest request) {

        log.error("Violación de integridad en {}", request.getRequestURI(), e);
        return build(HttpStatus.CONFLICT,
                "La operación viola una restricción de la base de datos "
                        + "(dato duplicado o referencia inexistente)",
                request);
    }

    // ======================== Red de seguridad ============================

    /**
     * 500: cualquier cosa que no se previó.
     *
     * <p>Se loguea con stack trace completo y al cliente se le devuelve un
     * mensaje neutro: filtrar acá evita que un NullPointerException termine
     * mostrándole al usuario rutas de clases internas.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e, HttpServletRequest request) {

        log.error("Error no controlado en {}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado en el servidor",
                request);
    }

    // =========================== Utilitario ===============================

    /** Arma la respuesta para que ningún handler repita el mismo bloque. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String message,
                                                HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
