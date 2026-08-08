package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import com.sebasmalparqueado.cowsvsclown.cows.controller.CowController;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that {@link GlobalExceptionHandler} is actually wired into the MVC stack.
 *
 * <p>{@code GlobalExceptionHandlerTest} already checks each handler's logic in
 * isolation; what is tested here is the part a unit test cannot see: that Spring
 * picks the right handler for each exception, that the status code reaches the
 * HTTP response, and that the body is serialized as the JSON the client expects.</p>
 *
 * <p>{@link CowController} is used as the entry point because it exercises every
 * interesting case: UUID in the path, a validated {@code @RequestParam}, and a
 * {@code @Valid} body.</p>
 */
@WebMvcTest(CowController.class)
// Turns the Spring Security filter chain off for this class. Without it every
// POST, PATCH and DELETE below would answer 401 and would no longer be testing
// what it means to test: the HTTP contract of the controller.
//
// It is not a way of dodging security. The rules live in one place,
// SecurityConfig, and are verified in one place, SecurityConfigIntegrationTest,
// which does run with the chain on. Duplicating them here would mean four
// classes to update every time a route changes.
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CowService cowService;

    // ======================= Business Exceptions ==========================

    @Nested
    @DisplayName("Business exceptions thrown by the service")
    class BusinessExceptions {

        @Test
        @DisplayName("ResourceNotFoundException comes out as 404 with the standard body")
        void notFoundBecomes404() throws Exception {
            UUID id = UUID.randomUUID();
            when(cowService.getById(id))
                    .thenThrow(ResourceNotFoundException.of("Cow", id));

            mockMvc.perform(get("/api/cows/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("There is no Cow with id " + id))
                    .andExpect(jsonPath("$.path").value("/api/cows/" + id))
                    .andExpect(jsonPath("$.timestamp").exists())
                    // The field must not travel when it is not a validation error.
                    .andExpect(jsonPath("$.validationErrors").doesNotExist());
        }

        @Test
        @DisplayName("ConflictException comes out as 409")
        void conflictBecomes409() throws Exception {
            when(cowService.create(any()))
                    .thenThrow(new ConflictException("There is already a cow named Lola"));

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Lola","weight":450,"milkperday":12,"ownerId":1}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("There is already a cow named Lola"));
        }

        @Test
        @DisplayName("BadRequestException comes out as 400")
        void badRequestBecomes400() throws Exception {
            when(cowService.create(any()))
                    .thenThrow(new BadRequestException("the owner has no room for more cows"));

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Lola","weight":450,"milkperday":12,"ownerId":1}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("the owner has no room for more cows"));
        }
    }

    // ========================== Validation ============================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("invalid body -> 400 with the field -> message map")
        void invalidBodyBecomes400WithFieldMap() throws Exception {
            // Empty name and negative weight: two different rules broken.
            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"","weight":-5,"milkperday":12,"ownerId":1}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("There are invalid fields in the request"))
                    .andExpect(jsonPath("$.validationErrors.name").exists())
                    .andExpect(jsonPath("$.validationErrors.weight")
                            .value("weight must be greater than 0"));

            verify(cowService, never()).create(any());
        }

        @Test
        @DisplayName("missing mandatory field -> 400 pointing at that field")
        void missingMandatoryFieldBecomes400() throws Exception {
            // No ownerId: the 1 to N relationship is mandatory.
            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Lola","weight":450,"milkperday":12}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.ownerId")
                            .value("the cow must have an owner"));
        }

        @Test
        @DisplayName("blank @RequestParam -> 400 through ConstraintViolationException")
        void blankRequestParamBecomes400() throws Exception {
            mockMvc.perform(get("/api/cows/search").param("name", "   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("There are invalid parameters in the request"))
                    .andExpect(jsonPath("$.validationErrors").exists());

            verify(cowService, never()).getByName(any());
        }

        @Test
        @DisplayName("missing @RequestParam -> 400 naming the parameter")
        void missingRequestParamBecomes400() throws Exception {
            mockMvc.perform(get("/api/cows/search"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Missing required parameter 'name'"));
        }
    }

    // ====================== Malformed Requests ========================

    @Nested
    @DisplayName("Malformed requests")
    class MalformedRequests {

        @Test
        @DisplayName("broken JSON -> 400 without leaking the parser detail")
        void brokenJsonBecomes400() throws Exception {
            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Lola\", }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "The request body is not valid JSON or a field has an unexpected type"));
        }

        @Test
        @DisplayName("field with the wrong type -> 400")
        void wrongTypeInBodyBecomes400() throws Exception {
            // weight receives text where an int is expected.
            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Lola","weight":"mucho","milkperday":12,"ownerId":1}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "The request body is not valid JSON or a field has an unexpected type"));
        }

        @Test
        @DisplayName("malformed UUID in the path -> 400 naming the parameter")
        void malformedUuidBecomes400() throws Exception {
            mockMvc.perform(get("/api/cows/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "The value 'not-a-uuid' is not valid for the parameter 'id' "
                                    + "(expected UUID)"));

            verify(cowService, never()).getById(any());
        }

        @Test
        @DisplayName("text where a Long is expected -> 400")
        void malformedLongBecomes400() throws Exception {
            mockMvc.perform(get("/api/cows/owner/{ownerId}", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "The value 'abc' is not valid for the parameter 'ownerId' "
                                    + "(expected Long)"));
        }
    }

    // ========================= Route Errors ===========================

    @Nested
    @DisplayName("Route errors")
    class RoutingErrors {

        @Test
        @DisplayName("verb not allowed on an existing route -> 405")
        void unsupportedMethodBecomes405() throws Exception {
            // /api/cows/{id} accepts GET, PATCH and DELETE, but not PUT.
            mockMvc.perform(put("/api/cows/{id}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith(
                                    "The PUT method is not allowed on this path")));
        }
    }

    // ====================== Safety Nets ========================

    @Nested
    @DisplayName("Safety nets")
    class SafetyNets {

        @Test
        @DisplayName("integrity violation -> 409 without exposing the constraint")
        void dataIntegrityBecomes409() throws Exception {
            when(cowService.create(any())).thenThrow(new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"cows_name_key\""));

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Lola","weight":450,"milkperday":12,"ownerId":1}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "The operation violates a database constraint "
                                    + "(duplicated data or missing reference)"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("cows_name_key"))));
        }

        @Test
        @DisplayName("unexpected exception -> 500 without leaking internal detail")
        void unexpectedBecomes500() throws Exception {
            UUID id = UUID.randomUUID();
            when(cowService.getById(id))
                    .thenThrow(new IllegalStateException("connection pool exhausted"));

            mockMvc.perform(get("/api/cows/{id}", id))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message")
                            .value("An unexpected server error occurred"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("connection pool"))));
        }
    }
}
