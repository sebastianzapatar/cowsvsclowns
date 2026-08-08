package com.sebasmalparqueado.cowsvsclown.clowns.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.service.ClownService;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests for {@link ClownController}.
 * Uses {@code @WebMvcTest} to test the web layer in isolation.
 *
 * <p>Same shape as the other two controller tests — the real Spring MVC stack
 * with the service mocked, asserting the HTTP contract rather than business
 * rules. What is specific to this one is the pair of endpoints that manage the
 * N to M link:</p>
 *
 * <pre>
 *   POST   /api/clowns/{clownId}/cows/{cowId}   assign
 *   DELETE /api/clowns/{clownId}/cows/{cowId}   unassign
 * </pre>
 *
 * <p>They live under {@code /api/clowns} and not {@code /api/cows} because
 * {@code Clown} is the owning side of the relationship. And they are modelled as
 * a sub-resource rather than a field in the PATCH body because a link is not a
 * property of the clown: it is something that gets created and deleted, which is
 * exactly what POST and DELETE mean.</p>
 *
 * <p>Note the status codes they return, asserted below: assigning answers
 * <b>200 with the updated clown</b>, not 201 — no new addressable resource is
 * born, since there is no URL for a single link.</p>
 */
@WebMvcTest(ClownController.class)
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
class ClownControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Replaces the real service: this slice has no database behind it. */
    @MockitoBean
    private ClownService clownService;

    // ============================== GET ===============================

    @Nested
    @DisplayName("GET /api/clowns")
    class GetAllClowns {

        @Test
        @DisplayName("returns 200 OK and list of clowns")
        void returnsOkAndList() throws Exception {
            ClownResponse response = new ClownResponse(
                    UUID.randomUUID(), "Pennywise", "Terror", true, 0, List.of());
            when(clownService.getAll()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Pennywise"));

            verify(clownService).getAll();
        }
    }

    @Nested
    @DisplayName("GET /api/clowns/{id}")
    class GetClownById {

        @Test
        @DisplayName("returns 200 OK if the clown exists")
        void returnsOkIfFound() throws Exception {
            UUID id = UUID.randomUUID();
            ClownResponse response = new ClownResponse(
                    id, "Pennywise", "Terror", true, 0, List.of());
            when(clownService.getById(id)).thenReturn(response);

            mockMvc.perform(get("/api/clowns/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Pennywise"));
        }

        @Test
        @DisplayName("returns 404 Not Found if missing")
        void returnsNotFoundIfMissing() throws Exception {
            UUID id = UUID.randomUUID();
            when(clownService.getById(id)).thenThrow(ResourceNotFoundException.of("Clown", id));

            mockMvc.perform(get("/api/clowns/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * Reads the N to M from the cow's end. It hangs off {@code /api/clowns}
     * because what comes back is a list of clowns — the route reflects the shape
     * of the answer, not the id in the path.
     */
    @Nested
    @DisplayName("GET /api/clowns/cow/{cowId}")
    class GetClownsByCow {

        @Test
        @DisplayName("returns 200 OK and list of clowns for a cow")
        void returnsOkAndList() throws Exception {
            UUID cowId = UUID.randomUUID();
            ClownResponse response = new ClownResponse(
                    UUID.randomUUID(), "Pennywise", "Terror", true, 0, List.of());
            when(clownService.getByCow(cowId)).thenReturn(List.of(response));

            mockMvc.perform(get("/api/clowns/cow/{cowId}", cowId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Pennywise"));
        }
    }

    // ============================== POST ===============================

    @Nested
    @DisplayName("POST /api/clowns")
    class CreateClown {

        @Test
        @DisplayName("returns 201 Created when valid")
        void returnsCreatedWhenValid() throws Exception {
            ClownRequest request = new ClownRequest("Bozo", "Feliz", List.of());
            UUID id = UUID.randomUUID();
            ClownResponse response = new ClownResponse(
                    id, "Bozo", "Feliz", true, 0, List.of());

            when(clownService.create(any(ClownRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/clowns/" + id))
                    .andExpect(jsonPath("$.name").value("Bozo"));
        }

        /**
         * Rejected by {@code @Valid} before the controller body runs, so the
         * service is never reached.
         */
        @Test
        @DisplayName("returns 400 Bad Request if missing fields")
        void returnsBadRequest() throws Exception {
            // Blank strings rather than nulls: @NotBlank rejects both, and blanks
            // are what a form actually submits.
            ClownRequest request = new ClownRequest("", "", List.of());

            mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================== PATCH ===============================

    @Nested
    @DisplayName("PATCH /api/clowns/{id}")
    class UpdateClown {

        @Test
        @DisplayName("returns 200 OK when valid")
        void returnsOkWhenValid() throws Exception {
            UUID id = UUID.randomUUID();
            ClownUpdateRequest request = new ClownUpdateRequest(null, "Sad");
            ClownResponse response = new ClownResponse(
                    id, "Bozo", "Sad", true, 0, List.of());

            when(clownService.update(eq(id), any(ClownUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(patch("/api/clowns/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Sad"));
        }
    }

    /**
     * Creating the N to M link. POST because a link is brought into existence,
     * but <b>200 and not 201</b>: 201 promises a Location header pointing at the
     * new resource, and a single link has no URL of its own. What comes back is
     * the updated clown.
     */
    @Nested
    @DisplayName("POST /api/clowns/{id}/cows/{cowId}")
    class AddCowToClown {

        @Test
        @DisplayName("returns 200 OK when cow is added")
        void returnsOk() throws Exception {
            UUID id = UUID.randomUUID();
            UUID cowId = UUID.randomUUID();
            ClownResponse response = new ClownResponse(
                    id, "Pennywise", "Terror", true, 1, List.of());

            when(clownService.assignCow(id, cowId)).thenReturn(response);

            // No body: both ids are in the path, which together name the link
            // being created.
            mockMvc.perform(post("/api/clowns/{id}/cows/{cowId}", id, cowId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // ============================== DELETE ===============================

    /**
     * Destroying the link — and the one DELETE in the project that really
     * deletes: the {@code clown_cow} row is removed rather than flagged.
     *
     * <p>It answers <b>200 with the updated clown</b>, not the 204 that
     * {@code DELETE /api/clowns/{id}} returns. The difference is deliberate:
     * deleting the clown leaves nothing to talk about, while unassigning a cow
     * leaves a clown whose new state the caller usually wants to see.</p>
     */
    @Nested
    @DisplayName("DELETE /api/clowns/{id}/cows/{cowId}")
    class RemoveCowFromClown {

        @Test
        @DisplayName("returns 200 OK when cow is removed")
        void returnsOk() throws Exception {
            UUID id = UUID.randomUUID();
            UUID cowId = UUID.randomUUID();
            ClownResponse response = new ClownResponse(
                    id, "Pennywise", "Terror", true, 0, List.of());

            when(clownService.unassignCow(id, cowId)).thenReturn(response);

            mockMvc.perform(delete("/api/clowns/{id}/cows/{cowId}", id, cowId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/clowns/{id}")
    class DeleteClown {

        @Test
        @DisplayName("returns 204 No Content")
        void returnsNoContent() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(clownService).softDelete(id);

            mockMvc.perform(delete("/api/clowns/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(clownService).softDelete(id);
        }
    }
}
