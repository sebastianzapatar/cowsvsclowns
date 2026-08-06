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
 *
 * <p>Why that distinction earns its own block of tests: every error the API can
 * produce goes out through this record, so its shape is the part of the contract
 * clients actually code against. A field that silently starts appearing as
 * {@code null} instead of being omitted is the kind of change that breaks a
 * strict client without breaking a single assertion elsewhere.</p>
 */
class ErrorResponseTest {

    /**
     * Built by hand rather than injected. These tests are deliberately outside
     * Spring, so this mapper is not necessarily configured like the application's
     * — {@code findAndRegisterModules()} is what pulls in JSR-310 so the
     * {@code LocalDateTime} timestamp can be written at all.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** The general factory: used by every error that is not a field validation. */
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

        /**
         * The {@code error} text is derived from the status rather than passed
         * in, so the two can never disagree — a hand-written "Not Found" next to
         * a 409 is exactly the sort of thing that confuses whoever is debugging
         * against the API. Three statuses are checked, including two-word ones,
         * because that is where a naive implementation would slip.
         */
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
            // Sandwiched between two readings of the clock instead of compared to
            // a fixed value: the timestamp is generated inside the factory, so it
            // cannot be predicted, only bounded. Asserting an exact instant here
            // would be flaky by construction.
            LocalDateTime before = LocalDateTime.now();
            ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, "x", "/p");
            LocalDateTime after = LocalDateTime.now();

            // isBefore/isAfter negated rather than isEqual, so a clock with
            // coarse resolution (where all three readings land on the same tick)
            // still passes.
            assertFalse(response.timestamp().isBefore(before));
            assertFalse(response.timestamp().isAfter(after));
        }
    }

    /**
     * The specialised factory for {@code @Valid} failures. It is separate from
     * {@code of} because it is the only path that carries the per-field map, and
     * it hard-codes 400 — a field validation cannot be anything else.
     */
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

        /**
         * The factory must not re-bucket the map into a {@code HashMap}, which
         * would scramble the order. The handler feeds it a {@code LinkedHashMap}
         * built in the order the fields are declared, so a form highlighting
         * errors gets them top to bottom instead of shuffled on every request.
         */
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

    /**
     * The block that actually asserts the contract. Everything above checks what
     * the record <em>holds</em>; what the client receives is what Jackson
     * <em>writes</em>, and the two are not the same thing — {@code @JsonInclude}
     * sits between them. Serialising for real is the only way to catch an
     * annotation that was dropped or misconfigured.
     */
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

        /**
         * Pins down the boundary of {@code NON_NULL} so nobody "fixes" it into
         * {@code NON_EMPTY} later. The difference is meaningful: an absent field
         * means "this was not a validation error", while an empty map means "it
         * was, but no field could be attributed" — collapsing the two would hide
         * a bug in the handler behind what looks like a normal response.
         */
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
