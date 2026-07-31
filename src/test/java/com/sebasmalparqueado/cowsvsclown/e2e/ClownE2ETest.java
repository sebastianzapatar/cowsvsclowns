package com.sebasmalparqueado.cowsvsclown.e2e;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clown end-to-end tests. Covers the N to M relationship with cows and
 * CRUD operations with real HTTP requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClownE2ETest {

    @Autowired
    private TestRestTemplate rest;

    private static final String CLOWNS_URL = "/api/clowns";
    private static final String COWS_URL = "/api/cows";
    private static final String OWNERS_URL = "/api/owners";

    /** Creates a test owner and cow, returns the cow's id. */
    private UUID createTestCow(String cowName) {
        OwnerResponse owner = rest.postForEntity(OWNERS_URL,
                new OwnerRequest("Test", "Owner", null), OwnerResponse.class).getBody();
        CowResponse cow = rest.postForEntity(COWS_URL,
                new CowRequest(cowName, 450, 12, owner.id(), null), CowResponse.class).getBody();
        return cow.id();
    }

    // ============================== POST =================================

    @Test
    @Order(1)
    @DisplayName("POST /api/clowns — 201: creates clown without cows")
    void createClown_returns201() {
        ClownRequest request = new ClownRequest("Pennywise", "Terrorífico", null);

        ResponseEntity<ClownResponse> response = rest.postForEntity(
                CLOWNS_URL, request, ClownResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Pennywise", response.getBody().name());
        assertEquals(0, response.getBody().totalCows());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/clowns — 201: creates clown with assigned cows")
    void createClown_withCows_returns201() {
        UUID cowId = createTestCow("Lola");
        ClownRequest request = new ClownRequest("Pennywise", "Terror", List.of(cowId));

        ResponseEntity<ClownResponse> response = rest.postForEntity(
                CLOWNS_URL, request, ClownResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().totalCows());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/clowns — 409: duplicate name")
    void createClown_duplicate_returns409() {
        ClownRequest request = new ClownRequest("Pennywise", null, null);
        rest.postForEntity(CLOWNS_URL, request, ClownResponse.class);

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                CLOWNS_URL, request, ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // ============================== GET ==================================

    @Test
    @Order(4)
    @DisplayName("GET /api/clowns — 200: list clowns")
    void getAllClowns_returns200() {
        rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class);

        ResponseEntity<List<ClownResponse>> response = rest.exchange(
                CLOWNS_URL, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/clowns/{id} — 200: clown found")
    void getClownById_returns200() {
        ClownResponse created = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class).getBody();

        ResponseEntity<ClownResponse> response = rest.getForEntity(
                CLOWNS_URL + "/" + created.id(), ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Pennywise", response.getBody().name());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/clowns/cow/{cowId} — 200: clowns of a cow")
    void getClownsByCow_returns200() {
        UUID cowId = createTestCow("Lola");
        rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, List.of(cowId)), ClownResponse.class);

        ResponseEntity<List<ClownResponse>> response = rest.exchange(
                CLOWNS_URL + "/cow/" + cowId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // ============================== N a M ================================

    @Test
    @Order(7)
    @DisplayName("POST /api/clowns/{id}/cows/{cowId} — 200: assigns cow")
    void assignCow_returns200() {
        UUID cowId = createTestCow("Lola");
        ClownResponse clown = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class).getBody();

        ResponseEntity<ClownResponse> response = rest.postForEntity(
                CLOWNS_URL + "/" + clown.id() + "/cows/" + cowId,
                null, ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().totalCows());
    }

    @Test
    @Order(8)
    @DisplayName("POST /api/clowns/{id}/cows/{cowId} — 409: cow already assigned")
    void assignCow_duplicate_returns409() {
        UUID cowId = createTestCow("Lola");
        ClownResponse clown = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, List.of(cowId)), ClownResponse.class).getBody();

        // Try assigning the same cow again
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                CLOWNS_URL + "/" + clown.id() + "/cows/" + cowId,
                null, ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @Order(9)
    @DisplayName("DELETE /api/clowns/{id}/cows/{cowId} — 200: unassigns cow")
    void unassignCow_returns200() {
        UUID cowId = createTestCow("Lola");
        ClownResponse clown = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, List.of(cowId)), ClownResponse.class).getBody();

        ResponseEntity<ClownResponse> response = rest.exchange(
                CLOWNS_URL + "/" + clown.id() + "/cows/" + cowId,
                HttpMethod.DELETE, null, ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().totalCows());
    }

    // ============================== PATCH ================================

    @Test
    @Order(10)
    @DisplayName("PATCH /api/clowns/{id} — 200: updates name")
    void updateClown_returns200() {
        ClownResponse created = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class).getBody();

        ClownUpdateRequest update = new ClownUpdateRequest("Bozo", null);
        HttpEntity<ClownUpdateRequest> entity = new HttpEntity<>(update);

        ResponseEntity<ClownResponse> response = rest.exchange(
                CLOWNS_URL + "/" + created.id(), HttpMethod.PATCH, entity, ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Bozo", response.getBody().name());
    }

    // ============================== DELETE ================================

    @Test
    @Order(11)
    @DisplayName("DELETE /api/clowns/{id} — 204: logical delete")
    void deleteClown_returns204() {
        ClownResponse created = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class).getBody();

        ResponseEntity<Void> response = rest.exchange(
                CLOWNS_URL + "/" + created.id(), HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
