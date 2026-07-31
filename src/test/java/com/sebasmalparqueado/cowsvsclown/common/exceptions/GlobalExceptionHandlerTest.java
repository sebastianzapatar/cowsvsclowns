package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each handler is invoked directly instead of going through MockMvc. That way
 * cases that are hard to provoke from a real request can also be covered
 * (a database integrity violation, or an unexpected exception), and the exact
 * body that reaches the client is asserted, not just the status code.</p>
 *
 * <p>The end-to-end wiring (that Spring really routes exceptions here) is
 * verified in {@code GlobalExceptionHandlerIntegrationTest}.</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final String PATH = "/api/cows/3fa85f64-5717-4562-b3fc-2c963f66afa6";

    private HttpServletRequest request() {
        return new MockHttpServletRequest("GET", PATH);
    }

    // ======================= Custom Exceptions ==========================

    @Nested
    @DisplayName("Business exceptions")
    class BusinessExceptions {

        @Test
        @DisplayName("ResourceNotFoundException -> 404 keeping the original message")
        void resourceNotFoundReturns404() {
            ResourceNotFoundException e = ResourceNotFoundException.of("Cow", 7);

            ResponseEntity<ErrorResponse> response =
                    handler.handleResourceNotFound(e, request());

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(404, body.status());
            assertEquals("Not Found", body.error());
            assertEquals("There is no Cow with id 7", body.message());
            assertEquals(PATH, body.path());
            // Not a validation error: the map must be absent so it is not serialized.
            assertNull(body.validationErrors());
        }

        @Test
        @DisplayName("BadRequestException -> 400 keeping the original message")
        void badRequestReturns400() {
            BadRequestException e = new BadRequestException("cannot assign an inactive cow");

            ResponseEntity<ErrorResponse> response =
                    handler.handleBadRequest(e, request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(400, body.status());
            assertEquals("Bad Request", body.error());
            assertEquals("cannot assign an inactive cow", body.message());
            assertNull(body.validationErrors());
        }

        @Test
        @DisplayName("ConflictException -> 409 keeping the original message")
        void conflictReturns409() {
            ConflictException e = new ConflictException("There is already a cow named Lola");

            ResponseEntity<ErrorResponse> response =
                    handler.handleConflict(e, request());

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(409, body.status());
            assertEquals("Conflict", body.error());
            assertEquals("There is already a cow named Lola", body.message());
        }

        @Test
        @DisplayName("the timestamp of the response is filled in")
        void timestampIsPresent() {
            LocalDateTime before = LocalDateTime.now();

            ErrorResponse body = handler
                    .handleConflict(new ConflictException("x"), request())
                    .getBody();

            assertNotNull(body);
            assertNotNull(body.timestamp());
            assertFalse(body.timestamp().isBefore(before));
        }
    }

    // ==================== Request Body Validation =======================

    @Nested
    @DisplayName("MethodArgumentNotValidException (@Valid @RequestBody)")
    class BodyValidation {

        @Test
        @DisplayName("maps each field to its message")
        void mapsFieldToMessage() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new FieldError("cowRequest", "name", "name is mandatory"));
            binding.addError(new FieldError("cowRequest", "weight", "weight must be greater than 0"));

            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("There are invalid fields in the request", body.message());
            assertEquals(PATH, body.path());
            assertNotNull(body.validationErrors());
            assertEquals(2, body.validationErrors().size());
            assertEquals("name is mandatory", body.validationErrors().get("name"));
            assertEquals("weight must be greater than 0", body.validationErrors().get("weight"));
        }

        @Test
        @DisplayName("concatenates both messages when one field breaks two rules")
        void mergesTwoErrorsOnTheSameField() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new FieldError("cowRequest", "name", "name is mandatory"));
            binding.addError(new FieldError("cowRequest", "name",
                    "name must be between 2 and 100 characters"));

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            // This is the point of using merge() and not put(): no message is lost.
            assertEquals(1, body.validationErrors().size());
            assertEquals("name is mandatory; name must be between 2 and 100 characters",
                    body.validationErrors().get("name"));
        }

        @Test
        @DisplayName("groups class-level errors under the key 'object'")
        void groupsGlobalErrorsUnderObject() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new ObjectError("cowRequest", "a cow cannot be its own mother"));

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            assertEquals("a cow cannot be its own mother", body.validationErrors().get("object"));
        }

        @Test
        @DisplayName("concatenates several class-level errors under 'object'")
        void mergesSeveralGlobalErrors() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new ObjectError("cowRequest", "first rule"));
            binding.addError(new ObjectError("cowRequest", "second rule"));

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            assertEquals("first rule; second rule", body.validationErrors().get("object"));
        }

        @Test
        @DisplayName("keeps field errors and class-level errors together")
        void keepsFieldAndGlobalErrorsTogether() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new FieldError("cowRequest", "name", "name is mandatory"));
            binding.addError(new ObjectError("cowRequest", "inconsistent combination"));

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            assertEquals(2, body.validationErrors().size());
            assertEquals("name is mandatory", body.validationErrors().get("name"));
            assertEquals("inconsistent combination", body.validationErrors().get("object"));
        }

        @Test
        @DisplayName("does not break if the error has no message")
        void handlesNullDefaultMessage() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");
            binding.addError(new FieldError(
                    "cowRequest", "name", null, false, null, null, null));

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            // String.valueOf(null) instead of an NPE: better a "null" than a 500.
            assertEquals("null", body.validationErrors().get("name"));
        }

        @Test
        @DisplayName("returns an empty map if there are no errors")
        void emptyMapWhenNoErrors() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "cowRequest");

            ErrorResponse body = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(dummyParameter(), binding), request()
            ).getBody();

            assertNotNull(body);
            assertNotNull(body.validationErrors());
            assertTrue(body.validationErrors().isEmpty());
        }
    }

    // ================== Parameter Validation ====================

    @Nested
    @DisplayName("ConstraintViolationException (@PathVariable / @RequestParam)")
    class ParameterValidation {

        @Test
        @DisplayName("maps each violated parameter to its message")
        void mapsParameterToMessage() {
            ConstraintViolationException e = new ConstraintViolationException(violationsOf(
                    new SearchParams("  ", 0)));

            ResponseEntity<ErrorResponse> response =
                    handler.handleConstraintViolation(e, request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("There are invalid parameters in the request", body.message());
            assertNotNull(body.validationErrors());
            assertEquals("the search name cannot be empty", body.validationErrors().get("name"));
            assertEquals("page cannot be less than 1", body.validationErrors().get("page"));
        }

        @Test
        @DisplayName("returns an empty map if the set of violations is empty")
        void emptyMapWhenNoViolations() {
            ConstraintViolationException e = new ConstraintViolationException(Set.of());

            ErrorResponse body = handler.handleConstraintViolation(e, request()).getBody();

            assertNotNull(body);
            assertTrue(body.validationErrors().isEmpty());
        }

        /** Small record only used to produce real violations with the Bean Validation engine. */
        record SearchParams(
                @NotBlank(message = "the search name cannot be empty") String name,
                @Min(value = 1, message = "page cannot be less than 1") int page) {
        }

        private Set<ConstraintViolation<SearchParams>> violationsOf(SearchParams params) {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                Validator validator = factory.getValidator();
                return validator.validate(params);
            }
        }
    }

    // ==================== Malformed Requests =====================

    @Nested
    @DisplayName("Malformed requests")
    class MalformedRequests {

        @Test
        @DisplayName("unreadable JSON -> 400 without leaking the parser detail")
        void unreadableBodyReturns400() {
            HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                    "JSON parse error: Unexpected character ('}' (code 125)) at [Source: line 3]",
                    emptyInputMessage());

            ResponseEntity<ErrorResponse> response = handler.handleNotReadable(e, request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("The request body is not valid JSON or a field has an unexpected type",
                    body.message());
            // The internal detail is logged, not returned: it is noise for the client.
            assertFalse(body.message().contains("JSON parse error"));
        }

        @Test
        @DisplayName("type mismatch -> 400 naming the value, the parameter and the expected type")
        void typeMismatchReturns400() {
            MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                    "not-a-uuid", UUID.class, "id", dummyParameter(), null);

            ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(e, request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("The value 'not-a-uuid' is not valid for the parameter 'id' (expected UUID)",
                    body.message());
        }

        @Test
        @DisplayName("type mismatch without a known type -> generic wording")
        void typeMismatchWithoutRequiredType() {
            MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                    "abc", null, "id", dummyParameter(), null);

            ErrorResponse body = handler.handleTypeMismatch(e, request()).getBody();

            assertNotNull(body);
            // Covers the getRequiredType() == null branch.
            assertEquals("The value 'abc' is not valid for the parameter 'id' "
                    + "(expected the expected type)", body.message());
        }

        @Test
        @DisplayName("missing required parameter -> 400 naming it")
        void missingParamReturns400() {
            MissingServletRequestParameterException e =
                    new MissingServletRequestParameterException("name", "String");

            ResponseEntity<ErrorResponse> response = handler.handleMissingParam(e, request());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("Missing required parameter 'name'", body.message());
        }
    }

    // ========================= Route Errors ============================

    @Nested
    @DisplayName("Route errors")
    class RoutingErrors {

        @Test
        @DisplayName("nonexistent path -> 404 naming the invoked path")
        void noResourceFoundReturns404() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chickens");
            NoResourceFoundException e =
                    new NoResourceFoundException(HttpMethod.GET, "/api/chickens", "/api/chickens");

            ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(e, request);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("The path /api/chickens does not exist in this API", body.message());
            assertEquals("/api/chickens", body.path());
        }

        @Test
        @DisplayName("unsupported verb -> 405 listing the allowed ones")
        void methodNotSupportedReturns405() {
            HttpRequestMethodNotSupportedException e =
                    new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "PATCH"));

            ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(e, request());

            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(405, body.status());
            assertTrue(body.message().startsWith(
                    "The PUT method is not allowed on this path. Allowed:"));
            assertTrue(body.message().contains("GET"));
            assertTrue(body.message().contains("PATCH"));
        }
    }

    // ====================== Database Errors ======================

    @Nested
    @DisplayName("Database errors")
    class DatabaseErrors {

        @Test
        @DisplayName("integrity violation -> 409 without exposing the constraint name")
        void dataIntegrityReturns409() {
            DataIntegrityViolationException e = new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint \"cows_name_key\"");

            ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(e, request());

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("The operation violates a database constraint "
                    + "(duplicated data or missing reference)", body.message());
            // The Postgres text exposes tables and constraints: it must not reach the client.
            assertFalse(body.message().contains("cows_name_key"));
        }
    }

    // ======================== Safety Net ============================

    @Nested
    @DisplayName("Safety net")
    class SafetyNet {

        @Test
        @DisplayName("unexpected exception -> 500 with a neutral message")
        void unexpectedReturns500() {
            Exception e = new IllegalStateException("connection pool exhausted");

            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(e, request());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

            ErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(500, body.status());
            assertEquals("Internal Server Error", body.error());
            assertEquals("An unexpected server error occurred", body.message());
            assertFalse(body.message().contains("connection pool"));
        }

        @Test
        @DisplayName("does not break with an exception without a message")
        void handlesExceptionWithoutMessage() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnexpected(new NullPointerException(), request());

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("An unexpected server error occurred", response.getBody().message());
        }
    }

    // =========================== Utility ===============================

    /**
     * Several Spring exceptions require a {@link MethodParameter}. Building one
     * needs a real method, so this one exists solely to be pointed at by reflection.
     */
    @SuppressWarnings("unused")
    private void dummyEndpoint(String value) {
    }

    private MethodParameter dummyParameter() {
        try {
            Method method = GlobalExceptionHandlerTest.class
                    .getDeclaredMethod("dummyEndpoint", String.class);
            return new MethodParameter(method, 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("dummyEndpoint should exist", e);
        }
    }

    /** Minimum needed to build a HttpMessageNotReadableException. */
    private HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }
}
