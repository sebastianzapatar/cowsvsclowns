package com.sebasmalparqueado.cowsvsclown.e2e;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
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
 * Cow end-to-end tests. Covers the 1 to N relationship with owners and
 * CRUD operations with real HTTP requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Sin Keycloak al lado: las peticiones salen firmadas como "admin".
@Import(E2EAuthConfig.class)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CowE2ETest {

    @Autowired
    private TestRestTemplate rest;

    private static final String COWS_URL = "/api/cows";
    private static final String OWNERS_URL = "/api/owners";

    /** Creates a test owner and returns its id. */
    private Long createTestOwner() {
        OwnerResponse owner = rest.postForEntity(OWNERS_URL,
                new OwnerRequest("Test", "Owner", null), OwnerResponse.class).getBody();
        return owner.id();
    }

    // ============================== POST =================================

    @Test
    @Order(1)
    @DisplayName("POST /api/cows — 201: creates cow with owner")
    void createCow_returns201() {
        Long ownerId = createTestOwner();
        CowRequest request = new CowRequest("Lola", 450, 12, ownerId, null);

        ResponseEntity<CowResponse> response = rest.postForEntity(
                COWS_URL, request, CowResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Lola", response.getBody().name());
        assertEquals(450, response.getBody().weight());
        assertNotNull(response.getBody().owner());
        assertEquals("Test Owner", response.getBody().owner().fullName());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/cows — 409: duplicate name")
    void createCow_duplicateName_returns409() {
        Long ownerId = createTestOwner();
        CowRequest request = new CowRequest("Lola", 450, 12, ownerId, null);
        rest.postForEntity(COWS_URL, request, CowResponse.class);

        // Second one with the same name
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                COWS_URL, request, ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/cows — 400: without name")
    void createCow_invalidBody_returns400() {
        Long ownerId = createTestOwner();
        CowRequest request = new CowRequest("", 0, 0, ownerId, null);

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                COWS_URL, request, ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ============================== GET ==================================

    @Test
    @Order(4)
    @DisplayName("GET /api/cows — 200: list cows")
    void getAllCows_returns200() {
        Long ownerId = createTestOwner();
        rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class);

        ResponseEntity<List<CowResponse>> response = rest.exchange(
                COWS_URL, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/cows/{id} — 200: cow found")
    void getCowById_returns200() {
        Long ownerId = createTestOwner();
        CowResponse created = rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class).getBody();

        ResponseEntity<CowResponse> response = rest.getForEntity(
                COWS_URL + "/" + created.id(), CowResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Lola", response.getBody().name());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/cows/search?name=Lola — 200: search by name")
    void searchCowByName_returns200() {
        Long ownerId = createTestOwner();
        rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class);

        ResponseEntity<CowResponse> response = rest.getForEntity(
                COWS_URL + "/search?name=Lola", CowResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Lola", response.getBody().name());
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/cows/owner/{ownerId} — 200: cows of an owner")
    void getCowsByOwner_returns200() {
        Long ownerId = createTestOwner();
        rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class);

        ResponseEntity<List<CowResponse>> response = rest.exchange(
                COWS_URL + "/owner/" + ownerId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // ============================== PATCH ================================

    @Test
    @Order(8)
    @DisplayName("PATCH /api/cows/{id} — 200: updates weight")
    void updateCow_returns200() {
        Long ownerId = createTestOwner();
        CowResponse created = rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class).getBody();

        CowUpdateRequest update = new CowUpdateRequest(null, 500, null);
        HttpEntity<CowUpdateRequest> entity = new HttpEntity<>(update);

        ResponseEntity<CowResponse> response = rest.exchange(
                COWS_URL + "/" + created.id(), HttpMethod.PATCH, entity, CowResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(500, response.getBody().weight());
        assertEquals("Lola", response.getBody().name()); // unchanged
    }

    @Test
    @Order(9)
    @DisplayName("PATCH /api/cows/{id}/owner/{ownerId} — 200: changes owner")
    void changeOwner_returns200() {
        Long ownerId1 = createTestOwner();
        CowResponse created = rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId1, null), CowResponse.class).getBody();

        // Create second owner
        OwnerResponse owner2 = rest.postForEntity(OWNERS_URL,
                new OwnerRequest("Otro", "Dueño", null), OwnerResponse.class).getBody();

        ResponseEntity<CowResponse> response = rest.exchange(
                COWS_URL + "/" + created.id() + "/owner/" + owner2.id(),
                HttpMethod.PATCH, null, CowResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Otro Dueño", response.getBody().owner().fullName());
    }

    // ============================== DELETE ================================

    @Test
    @Order(10)
    @DisplayName("DELETE /api/cows/{id} — 204: logical delete")
    void deleteCow_returns204() {
        Long ownerId = createTestOwner();
        CowResponse created = rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId, null), CowResponse.class).getBody();

        ResponseEntity<Void> response = rest.exchange(
                COWS_URL + "/" + created.id(), HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
