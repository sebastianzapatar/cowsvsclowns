package com.sebasmalparqueado.cowsvsclown.cows.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Controller integration tests for {@link CowController}.
 * Uses {@code @WebMvcTest} to test the web layer in isolation.
 *
 * <p>The request travels through the real Spring MVC stack — routing, JSON
 * binding, {@code @Valid}, {@code GlobalExceptionHandler} — with only the
 * service replaced. So what is under test is the <b>HTTP contract</b>: status
 * codes, headers and JSON shape, not business rules.</p>
 *
 * <p>This is the widest surface of the three controllers, with eight endpoints.
 * Two of them are worth singling out because they are not plain CRUD:</p>
 *
 * <ul>
 *   <li>{@code GET /api/cows/search}, the only one taking a query parameter
 *       instead of a path variable, which is a different binding path and its
 *       own kind of 400 when the parameter is missing.</li>
 *   <li>{@code PATCH /api/cows/{id}/owner/{ownerId}}, which moves a cow between
 *       owners. It is a separate endpoint rather than a field in the update body
 *       because it rewires a relationship instead of editing a value.</li>
 * </ul>
 */
@WebMvcTest(CowController.class)
@ActiveProfiles("test")
class CowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Replaces the real service: this slice has no database behind it. */
    @MockitoBean
    private CowService cowService;

    // ============================== GET ===============================

    @Nested
    @DisplayName("GET /api/cows")
    class GetAllCows {

        @Test
        @DisplayName("returns 200 OK and list of cows")
        void returnsOkAndList() throws Exception {
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(1L, "Sebastián Zapata");
            CowResponse response = new CowResponse(
                    UUID.randomUUID(), "Lola", 450, 12, true, ownerSummary, List.of());
            when(cowService.getCows()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Lola"));

            verify(cowService).getCows();
        }
    }

    @Nested
    @DisplayName("GET /api/cows/{id}")
    class GetCowById {

        @Test
        @DisplayName("returns 200 OK if the cow exists")
        void returnsOkIfFound() throws Exception {
            UUID id = UUID.randomUUID();
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(1L, "Sebastián Zapata");
            CowResponse response = new CowResponse(
                    id, "Lola", 450, 12, true, ownerSummary, List.of());
            
            when(cowService.getById(id)).thenReturn(response);

            mockMvc.perform(get("/api/cows/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Lola"));
        }

        @Test
        @DisplayName("returns 404 Not Found if missing")
        void returnsNotFoundIfMissing() throws Exception {
            UUID id = UUID.randomUUID();
            when(cowService.getById(id)).thenThrow(ResourceNotFoundException.of("Cow", id));

            mockMvc.perform(get("/api/cows/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * The only endpoint that takes a query parameter. It is a separate route
     * rather than {@code GET /api/cows/{name}} because a name is not an
     * identifier — the id is a UUID, and overloading the path would make
     * {@code /api/cows/Lola} ambiguous with a malformed UUID.
     */
    @Nested
    @DisplayName("GET /api/cows/search")
    class SearchCow {

        @Test
        @DisplayName("returns 200 OK when finding by name")
        void returnsOkIfFound() throws Exception {
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(1L, "Sebastián Zapata");
            CowResponse response = new CowResponse(
                    UUID.randomUUID(), "Lola", 450, 12, true, ownerSummary, List.of());

            when(cowService.getByName("Lola")).thenReturn(response);

            mockMvc.perform(get("/api/cows/search")
                            .param("name", "Lola")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Lola"));
        }

        /**
         * The parameter is present but blank, which is not the same as absent.
         * This 400 comes from {@code @NotBlank} on the parameter — a
         * {@code ConstraintViolationException}, handled on a different branch of
         * {@code GlobalExceptionHandler} than the {@code @Valid} failures on a
         * request body. Without the annotation the blank would sail through and
         * the query would run against an empty string.
         */
        @Test
        @DisplayName("returns 400 Bad Request if name is empty")
        void returnsBadRequestIfEmpty() throws Exception {
            mockMvc.perform(get("/api/cows/search")
                            .param("name", "")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================== POST ===============================

    @Nested
    @DisplayName("POST /api/cows")
    class CreateCow {

        @Test
        @DisplayName("returns 201 Created when valid")
        void returnsCreatedWhenValid() throws Exception {
            CowRequest request = new CowRequest("Margarita", 350, 8, 1L, List.of());
            UUID id = UUID.randomUUID();
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(1L, "Sebastián Zapata");
            CowResponse response = new CowResponse(
                    id, "Margarita", 350, 8, true, ownerSummary, List.of());

            when(cowService.create(any(CowRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    // The Location header carries the UUID the service assigned.
                    // Unlike the owner's sequential id, this one cannot be
                    // guessed by the client, so the header is the only way it
                    // learns where the new cow lives.
                    .andExpect(header().string("Location", "/api/cows/" + id))
                    .andExpect(jsonPath("$.name").value("Margarita"));
        }
    }

    // ============================== PATCH ===============================

    @Nested
    @DisplayName("PATCH /api/cows/{id}")
    class UpdateCow {

        @Test
        @DisplayName("returns 200 OK when valid")
        void returnsOkWhenValid() throws Exception {
            UUID id = UUID.randomUUID();
            // Only the weight travels: the two nulls are the point of PATCH, and
            // the response below still carries the original name and milk.
            CowUpdateRequest request = new CowUpdateRequest(null, 400, null);
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(1L, "Sebastián Zapata");
            CowResponse response = new CowResponse(
                    id, "Lola", 400, 12, true, ownerSummary, List.of());

            when(cowService.update(eq(id), any(CowUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(patch("/api/cows/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weight").value(400));
        }
    }

    /**
     * Moving a cow between owners. It has its own endpoint instead of being a
     * field in {@code CowUpdateRequest} because it rewires the 1 to N
     * relationship rather than editing a value — and both ids belong in the path,
     * since together they name the association being changed.
     */
    @Nested
    @DisplayName("PATCH /api/cows/{id}/owner/{ownerId}")
    class ChangeOwner {

        @Test
        @DisplayName("returns 200 OK when owner is changed")
        void returnsOkWhenValid() throws Exception {
            UUID id = UUID.randomUUID();
            OwnerSummaryResponse ownerSummary = new OwnerSummaryResponse(2L, "Juan Pérez");
            CowResponse response = new CowResponse(
                    id, "Lola", 450, 12, true, ownerSummary, List.of());

            when(cowService.changeOwner(id, 2L)).thenReturn(response);

            // No body at all: everything the operation needs is in the path.
            mockMvc.perform(patch("/api/cows/{id}/owner/{ownerId}", id, 2L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    // Asserted on the nested owner rather than the cow's own
                    // fields: the whole point of the call is that this changed.
                    .andExpect(jsonPath("$.owner.id").value(2));
        }
    }

    // ============================== DELETE ===============================

    @Nested
    @DisplayName("DELETE /api/cows/{id}")
    class DeleteCow {

        @Test
        @DisplayName("returns 204 No Content")
        void returnsNoContent() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(cowService).softDelete(id);

            mockMvc.perform(delete("/api/cows/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(cowService).softDelete(id);
        }
    }
}
