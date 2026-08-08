package com.sebasmalparqueado.cowsvsclown.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.owner.service.OwnerService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests for {@link OwnerController}.
 * Uses {@code @WebMvcTest} to test the web layer in isolation, mocking the
 * service layer.
 *
 * <p><b>Why these count as integration tests</b> even though the service is
 * mocked: nothing here is called directly. The request goes through the real
 * Spring MVC machinery — routing, JSON deserialisation, {@code @Valid},
 * {@code GlobalExceptionHandler}, then serialisation of the answer. That is a
 * lot of framework wiring, and it is precisely the part that a unit test of the
 * controller class would skip.</p>
 *
 * <p>{@code @WebMvcTest} loads only the web slice: this controller, the handler
 * advice and the Jackson setup. No database, no other controllers, no services —
 * hence the {@code @MockitoBean}.</p>
 *
 * <p>What is being verified here is the <b>HTTP contract</b>: status codes,
 * headers, and the shape of the JSON. Business rules are the service tests' job,
 * and the two together are what the e2e tests then exercise for real.</p>
 */
@WebMvcTest(OwnerController.class)
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
class OwnerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Replaces the real service in the context. Without it the slice would fail
     * to start, because the controller cannot be constructed without its
     * dependency.
     */
    @MockitoBean
    private OwnerService ownerService;

    // ============================== GET ===============================

    @Nested
    @DisplayName("GET /api/owners")
    class GetAllOwners {

        @Test
        @DisplayName("returns 200 OK and a list of owners")
        void returnsOkAndList() throws Exception {
            OwnerResponse response = new OwnerResponse(
                    1L, "Sebastián", "Zapata", "Sebastián Zapata", true, 0, List.of());
            when(ownerService.getAll()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].firstName").value("Sebastián"))
                    .andExpect(jsonPath("$[0].lastName").value("Zapata"));

            verify(ownerService).getAll();
        }
    }

    @Nested
    @DisplayName("GET /api/owners/{id}")
    class GetOwnerById {

        @Test
        @DisplayName("returns 200 OK if the owner exists")
        void returnsOkIfFound() throws Exception {
            OwnerResponse response = new OwnerResponse(
                    1L, "Sebastián", "Zapata", "Sebastián Zapata", true, 0, List.of());
            when(ownerService.getById(1L)).thenReturn(response);

            mockMvc.perform(get("/api/owners/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Sebastián"));

            verify(ownerService).getById(1L);
        }

        /**
         * The controller has no {@code try/catch}: the service throws and
         * {@code GlobalExceptionHandler} turns that into a 404 with the standard
         * body. This test is what proves the advice is actually wired into the
         * slice — the message asserted below is the one built by the exception,
         * so it also confirms nothing swallowed or rewrote it on the way out.
         */
        @Test
        @DisplayName("returns 404 Not Found if the owner does not exist")
        void returnsNotFoundIfMissing() throws Exception {
            when(ownerService.getById(99L)).thenThrow(ResourceNotFoundException.of("Owner", 99L));

            mockMvc.perform(get("/api/owners/{id}", 99L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("There is no Owner with id 99"));
        }
    }

    // ============================== POST ===============================

    @Nested
    @DisplayName("POST /api/owners")
    class CreateOwner {

        @Test
        @DisplayName("returns 201 Created when data is valid")
        void returnsCreatedWhenValid() throws Exception {
            OwnerRequest request = new OwnerRequest("Juan", "Pérez", null);
            OwnerResponse response = new OwnerResponse(
                    2L, "Juan", "Pérez", "Juan Pérez", true, 0, List.of());

            when(ownerService.create(any(OwnerRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    // 201 must carry a Location header pointing at the new
                    // resource. It is the part of REST most often left out, and
                    // the id in it comes from the response the service returned —
                    // so this also checks the controller reads it from the right
                    // place instead of echoing something from the request.
                    .andExpect(header().string("Location", "/api/owners/2"))
                    .andExpect(jsonPath("$.firstName").value("Juan"));
        }

        /**
         * The 400 here is raised by {@code @Valid} before the controller body
         * ever runs, so it exercises a different path from the business 400s in
         * the service tests.
         */
        @Test
        @DisplayName("returns 400 Bad Request when data is invalid")
        void returnsBadRequestWhenInvalid() throws Exception {
            // Empty strings rather than nulls: @NotBlank rejects both, and blanks
            // are what a form actually submits.
            OwnerRequest request = new OwnerRequest("", "", null);

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are invalid fields in the request"));

            // The service is never reached. This is what proves validation runs
            // at the boundary: if it were happening inside the service instead,
            // invalid data would already have crossed into the business layer.
            verify(ownerService, never()).create(any());
        }
    }

    // ============================== PATCH ===============================

    @Nested
    @DisplayName("PATCH /api/owners/{id}")
    class UpdateOwner {

        /**
         * PATCH and not PUT, because only the fields that are sent get changed.
         * The answer is 200 with the updated resource — not 206, which is for
         * ranged downloads and has nothing to do with partial updates.
         */
        @Test
        @DisplayName("returns 200 OK when update is valid")
        void returnsOkWhenValid() throws Exception {
            // Only the first name travels; the null last name is what makes this
            // a partial update.
            OwnerUpdateRequest request = new OwnerUpdateRequest("Mario", null);
            OwnerResponse response = new OwnerResponse(
                    1L, "Mario", "Zapata", "Mario Zapata", true, 0, List.of());

            when(ownerService.update(eq(1L), any(OwnerUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(patch("/api/owners/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Mario"));
        }
    }

    // ============================== DELETE ===============================

    @Nested
    @DisplayName("DELETE /api/owners/{id}")
    class DeleteOwner {

        /**
         * 204 and not 200: the deletion succeeded and there is no body worth
         * sending back. Note that from the client's side this looks like an
         * ordinary delete — that it is a soft delete underneath is an
         * implementation detail the HTTP contract does not expose.
         */
        @Test
        @DisplayName("returns 204 No Content on successful deletion")
        void returnsNoContentOnSuccess() throws Exception {
            doNothing().when(ownerService).softDelete(1L);

            mockMvc.perform(delete("/api/owners/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            // softDelete returns void, so there is no response to assert on:
            // verifying the call is the only way to know the controller did
            // anything at all.
            verify(ownerService).softDelete(1L);
        }
    }
}
