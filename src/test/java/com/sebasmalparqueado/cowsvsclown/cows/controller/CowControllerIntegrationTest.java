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
 * Controller integration tests for {@link CowController}.
 * Uses @WebMvcTest to test the web layer in isolation.
 */
@WebMvcTest(CowController.class)
// Estas pruebas miran el controller, no la seguridad: sin esta línea el filtro
// de Spring Security respondería 401 antes de que la petición llegue al método.
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

            mockMvc.perform(patch("/api/cows/{id}/owner/{ownerId}", id, 2L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
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
