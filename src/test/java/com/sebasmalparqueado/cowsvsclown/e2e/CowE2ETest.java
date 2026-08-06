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
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cow end-to-end tests. Covers the 1 to N relationship with owners and CRUD
 * operations with real HTTP requests.
 *
 * <p>Nothing is mocked: each request crosses a real socket, Tomcat, the
 * controller, the service and Hibernate, and every write commits for real. See
 * {@link OwnerE2ETest} for what each annotation on the class is doing — the
 * setup is identical.</p>
 *
 * <p>What is specific to this class is that a cow <b>cannot exist on its own</b>:
 * {@code cows.owner_id} is NOT NULL, so every test has to create an owner first.
 * That is what {@link #createTestOwner()} is for, and it is also why the setup
 * here is heavier than in the other two e2e classes.</p>
 *
 * <p>The interesting endpoint is {@code PATCH /api/cows/{id}/owner/{ownerId}},
 * which moves a cow between owners — the one operation that rewires the 1 to N
 * after the fact.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CowE2ETest {

    /** Does not throw on 4xx/5xx: it returns the response so it can be asserted. */
    @Autowired
    private TestRestTemplate rest;

    private static final String COWS_URL = "/api/cows";
    private static final String OWNERS_URL = "/api/owners";

    /**
     * Creates a test owner and returns its id.
     *
     * <p>Built through the API rather than by touching the repository, so the
     * fixture goes down the same path as production code. Each test calls it
     * again because {@code @DirtiesContext} wipes the database between methods —
     * the owner from the previous test is not there any more.</p>
     */
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
        // The owner arrived as a bare id in the request and comes back as a
        // resolved summary. That round trip is the 1 to N working end to end:
        // the service looked the owner up, the foreign key was written, and the
        // query that reads it back used JOIN FETCH — without it this would be a
        // LazyInitializationException instead of a name.
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

    /**
     * The only operation that rewires the 1 to N after the fact. It needs the
     * fullest fixture in the class — two owners and a cow — because there has to
     * be somewhere to move the cow to.
     */
    @Test
    @Order(9)
    @DisplayName("PATCH /api/cows/{id}/owner/{ownerId} — 200: changes owner")
    void changeOwner_returns200() {
        Long ownerId1 = createTestOwner();
        CowResponse created = rest.postForEntity(COWS_URL,
                new CowRequest("Lola", 450, 12, ownerId1, null), CowResponse.class).getBody();

        // The destination owner. Different name and last name from the helper's,
        // so the assertion below cannot pass by accident.
        OwnerResponse owner2 = rest.postForEntity(OWNERS_URL,
                new OwnerRequest("Otro", "Dueño", null), OwnerResponse.class).getBody();

        // Null body: both ids travel in the path.
        ResponseEntity<CowResponse> response = rest.exchange(
                COWS_URL + "/" + created.id() + "/owner/" + owner2.id(),
                HttpMethod.PATCH, null, CowResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // The response already shows the new owner, so the UPDATE committed and
        // the re-read picked it up within the same request.
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

        // Void.class: a 204 has no body to deserialise.
        ResponseEntity<Void> response = rest.exchange(
                COWS_URL + "/" + created.id(), HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // The row survives with active = false, but the API has to behave as if
        // it were gone. Deleting the cow does not touch its owner: the cascade
        // runs the other way (deleting an owner deactivates their cows), never
        // upwards.
        ResponseEntity<ErrorResponse> afterDelete = rest.getForEntity(
                COWS_URL + "/" + created.id(), ErrorResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDelete.getStatusCode());
    }
}
