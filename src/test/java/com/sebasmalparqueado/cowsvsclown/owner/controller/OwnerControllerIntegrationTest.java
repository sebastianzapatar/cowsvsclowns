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
 * Uses @WebMvcTest to test the web layer in isolation, mocking the service layer.
 */
@WebMvcTest(OwnerController.class)
@ActiveProfiles("test")
class OwnerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    .andExpect(header().string("Location", "/api/owners/2"))
                    .andExpect(jsonPath("$.firstName").value("Juan"));
        }

        @Test
        @DisplayName("returns 400 Bad Request when data is invalid")
        void returnsBadRequestWhenInvalid() throws Exception {
            // Missing first name and last name
            OwnerRequest request = new OwnerRequest("", "", null);

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are invalid fields in the request"));

            verify(ownerService, never()).create(any());
        }
    }

    // ============================== PATCH ===============================

    @Nested
    @DisplayName("PATCH /api/owners/{id}")
    class UpdateOwner {

        @Test
        @DisplayName("returns 200 OK when update is valid")
        void returnsOkWhenValid() throws Exception {
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

        @Test
        @DisplayName("returns 204 No Content on successful deletion")
        void returnsNoContentOnSuccess() throws Exception {
            doNothing().when(ownerService).softDelete(1L);

            mockMvc.perform(delete("/api/owners/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(ownerService).softDelete(1L);
        }
    }
}
