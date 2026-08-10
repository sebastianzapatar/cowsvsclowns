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
 * Uses @WebMvcTest to test the web layer in isolation.
 */
@WebMvcTest(ClownController.class)
@ActiveProfiles("test")
class ClownControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    UUID.randomUUID(), "Pennywise", "Terror",
                    true, 0, List.of());
            when(clownService.getAll()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").
                            value("Pennywise"));

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
                    .andExpect(jsonPath("$.name").
                            value("Pennywise"));
        }

        @Test
        @DisplayName("returns 404 Not Found if missing")
        void returnsNotFoundIfMissing() throws Exception {
            UUID id = UUID.randomUUID();
            when(clownService.getById(id)).
                    thenThrow(ResourceNotFoundException.of
                            ("Clown", id));

            mockMvc.perform(get("/api/clowns/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

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

        @Test
        @DisplayName("returns 400 Bad Request if missing fields")
        void returnsBadRequest() throws Exception {
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

            mockMvc.perform(post("/api/clowns/{id}/cows/{cowId}", id, cowId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // ============================== DELETE ===============================

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
