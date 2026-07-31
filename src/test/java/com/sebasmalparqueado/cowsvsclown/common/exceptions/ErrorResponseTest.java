package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ErrorResponse}, the format every API error goes out in.
 *
 * <p>Beyond the factory methods, the serialized JSON is asserted: the contract
 * with the client is what Jackson writes, not what the record holds. In particular,
 * {@code validationErrors} must disappear from the JSON when it is null, which is
 * what {@code @JsonInclude(NON_NULL)} is there for.</p>
 */
class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("fills status, error name, message and path")
        void fillsAllFields() {
            ErrorResponse response = ErrorResponse.of(
                    HttpStatus.NOT_FOUND, "There is no Cow with id 7", "/api/cows/7");

            assertEquals(404, response.status());
            assertEquals("Not Found", response.error());
            assertEquals("There is no Cow with id 7", response.message());
            assertEquals("/api/cows/7", response.path());
            assertNotNull(response.timestamp());
        }

        @Test
        @DisplayName("leaves validationErrors null because it is not a validation error")
        void leavesValidationErrorsNull() {
            ErrorResponse response = ErrorResponse.of(
                    HttpStatus.CONFLICT, "duplicated", "/api/cows");

            assertNull(response.validationErrors());
        }

        @Test
        @DisplayName("derives the error name from the status")
        void derivesErrorNameFromStatus() {
            assertEquals("Bad Request",
                    ErrorResponse.of(HttpStatus.BAD_REQUEST, "x", "/p").error());
            assertEquals("Internal Server Error",
                    ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "x", "/p").error());
            assertEquals("Method Not Allowed",
                    ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, "x", "/p").error());
        }

        @Test
        @DisplayName("the timestamp is the current instant")
        void timestampIsNow() {
            LocalDateTime before = LocalDateTime.now();
            ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, "x", "/p");
            LocalDateTime after = LocalDateTime.now();

            assertFalse(response.timestamp().isBefore(before));
            assertFalse(response.timestamp().isAfter(after));
        }
    }

    @Nested
    @DisplayName("ofValidation")
    class OfValidation {

        @Test
        @DisplayName("is always 400 and carries the field map")
        void alwaysBadRequestWithFieldMap() {
            Map<String, String> errors = Map.of("name", "name is mandatory");

            ErrorResponse response = ErrorResponse.ofValidation(
                    "There are invalid fields in the request", "/api/cows", errors);

            assertEquals(400, response.status());
            assertEquals("Bad Request", response.error());
            assertEquals("There are invalid fields in the request", response.message());
            assertEquals("/api/cows", response.path());
            assertEquals(errors, response.validationErrors());
        }

        @Test
        @DisplayName("preserves the order in which the fields were added")
        void preservesFieldOrder() {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("name", "mandatory");
            errors.put("weight", "must be greater than 0");
            errors.put("ownerId", "mandatory");

            ErrorResponse response = ErrorResponse.ofValidation("x", "/api/cows", errors);

            assertIterableEquals(
                    java.util.List.of("name", "weight", "ownerId"),
                    response.validationErrors().keySet());
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    class Serialization {

        @Test
        @DisplayName("omits validationErrors when there is none")
        void omitsValidationErrorsWhenNull() throws Exception {
            String json = objectMapper.writeValueAsString(
                    ErrorResponse.of(HttpStatus.NOT_FOUND, "not found", "/api/cows/7"));

            assertFalse(json.contains("validationErrors"),
                    "@JsonInclude(NON_NULL) should hide the field: " + json);
            assertTrue(json.contains("\"status\":404"));
            assertTrue(json.contains("\"error\":\"Not Found\""));
            assertTrue(json.contains("\"path\":\"/api/cows/7\""));
        }

        @Test
        @DisplayName("includes validationErrors when there are field errors")
        void includesValidationErrorsWhenPresent() throws Exception {
            String json = objectMapper.writeValueAsString(ErrorResponse.ofValidation(
                    "invalid fields", "/api/cows", Map.of("name", "name is mandatory")));

            assertTrue(json.contains("validationErrors"));
            assertTrue(json.contains("name is mandatory"));
        }

        @Test
        @DisplayName("an empty map is serialized, it is not omitted")
        void emptyMapIsStillSerialized() throws Exception {
            String json = objectMapper.writeValueAsString(
                    ErrorResponse.ofValidation("x", "/api/cows", Map.of()));

            // NON_NULL only hides nulls; an empty map does travel.
            assertTrue(json.contains("validationErrors"));
        }
    }
}
