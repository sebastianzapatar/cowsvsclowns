package com.sebasmalparqueado.cowsvsclown.e2e;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner end-to-end tests. They boot the full app with H2 and make
 * real HTTP requests with TestRestTemplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Sin Keycloak al lado: las peticiones salen firmadas como "admin".
@Import(E2EAuthConfig.class)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OwnerE2ETest {

    @Autowired
    private TestRestTemplate rest;

    private static final String BASE_URL = "/api/owners";

    // ============================== POST =================================

    @Test
    @Order(1)
    @DisplayName("POST /api/owners — 201: creates owner with cows (names as strings)")
    void createOwner_returns201_withCowNames() {
        OwnerRequest request = new OwnerRequest("Sebastián", "Zapata",
                List.of(new OwnerCowRequest("Lola", 450, 12),
                        new OwnerCowRequest("Margarita", 380, 8)));

        ResponseEntity<OwnerResponse> response = rest.postForEntity(
                BASE_URL, request, OwnerResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Sebastián", response.getBody().firstName());
        assertEquals(2, response.getBody().totalCows());
        // Las vacas vienen como List<String> (solo nombres)
        assertTrue(response.getBody().cows().contains("Lola"));
        assertTrue(response.getBody().cows().contains("Margarita"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/owners — 201: creates owner without cows")
    void createOwner_withoutCows_returns201() {
        OwnerRequest request = new OwnerRequest("Juan", "Pérez", null);

        ResponseEntity<OwnerResponse> response = rest.postForEntity(
                BASE_URL, request, OwnerResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(0, response.getBody().totalCows());
        assertTrue(response.getBody().cows().isEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/owners — 409: duplicate owner")
    void createOwner_duplicate_returns409() {
        OwnerRequest request = new OwnerRequest("Sebastián", "Zapata", null);
        // First creation
        rest.postForEntity(BASE_URL, request, OwnerResponse.class);
        // Second: should give 409
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                BASE_URL, request, ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/owners — 400: invalid body (without name)")
    void createOwner_invalidBody_returns400() {
        OwnerRequest request = new OwnerRequest("", "", null);

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                BASE_URL, request, ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ============================== GET ==================================

    @Test
    @Order(5)
    @DisplayName("GET /api/owners — 200: lists owners")
    void getAllOwners_returns200() {
        // Create one first
        rest.postForEntity(BASE_URL,
                new OwnerRequest("Sebastián", "Zapata", null), OwnerResponse.class);

        ResponseEntity<List<OwnerResponse>> response = rest.exchange(
                BASE_URL, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/owners/{id} — 200: owner found")
    void getOwnerById_returns200() {
        OwnerResponse created = rest.postForEntity(BASE_URL,
                new OwnerRequest("Sebastián", "Zapata", null), OwnerResponse.class).getBody();

        ResponseEntity<OwnerResponse> response = rest.getForEntity(
                BASE_URL + "/" + created.id(), OwnerResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Sebastián", response.getBody().firstName());
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/owners/{id} — 404: owner does not exist")
    void getOwnerById_notFound_returns404() {
        ResponseEntity<ErrorResponse> response = rest.getForEntity(
                BASE_URL + "/999", ErrorResponse.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ============================== PATCH ================================

    @Test
    @Order(8)
    @DisplayName("PATCH /api/owners/{id} — 200: updates name")
    void updateOwner_returns200() {
        OwnerResponse created = rest.postForEntity(BASE_URL,
                new OwnerRequest("Sebastián", "Zapata", null), OwnerResponse.class).getBody();

        OwnerUpdateRequest update = new OwnerUpdateRequest("Juan", null);
        HttpEntity<OwnerUpdateRequest> entity = new HttpEntity<>(update);

        ResponseEntity<OwnerResponse> response = rest.exchange(
                BASE_URL + "/" + created.id(), HttpMethod.PATCH, entity, OwnerResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Juan", response.getBody().firstName());
        // The last name did not change
        assertEquals("Zapata", response.getBody().lastName());
    }

    // ============================== DELETE ================================

    @Test
    @Order(9)
    @DisplayName("DELETE /api/owners/{id} — 204: logical delete")
    void deleteOwner_returns204() {
        OwnerResponse created = rest.postForEntity(BASE_URL,
                new OwnerRequest("Sebastián", "Zapata", null), OwnerResponse.class).getBody();

        ResponseEntity<Void> response = rest.exchange(
                BASE_URL + "/" + created.id(), HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify that it no longer appears
        ResponseEntity<ErrorResponse> getResponse = rest.getForEntity(
                BASE_URL + "/" + created.id(), ErrorResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
    }
}
