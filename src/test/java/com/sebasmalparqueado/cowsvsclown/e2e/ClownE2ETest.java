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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clown end-to-end tests. Covers the N to M relationship with cows and CRUD
 * operations with real HTTP requests.
 *
 * <p>Nothing is mocked: each request crosses a real socket, Tomcat, the
 * controller, the service and Hibernate, and every write commits. See
 * {@link OwnerE2ETest} for what each class annotation is doing — the setup is
 * identical.</p>
 *
 * <p>This is the class that proves the <b>N to M</b> works, which is the hardest
 * of the two relationships to get right and the one where a mocked test proves
 * the least. {@code Clown} is the owning side (it declares the
 * {@code @JoinTable}), so Hibernate only writes {@code clown_cow} rows when the
 * change is made from this end. A unit test can show the in-memory collection
 * was updated; only a real commit followed by a real read shows the join row
 * actually exists.</p>
 *
 * <p>Note how deep the fixture has to go: to link a clown to a cow, the cow needs
 * an owner first, because {@code cows.owner_id} is NOT NULL. So every test in the
 * N to M block builds owner → cow → clown → link. That is what
 * {@link #createTestCow(String)} hides.</p>
 */
// Replaces the JwtDecoder with a test double, so tokens can be faked without a
// running Keycloak. Everything after that step — filters, role conversion, the
// rules — stays real. See E2EAuth for the full reasoning.
@Import(E2EAuth.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClownE2ETest {

    /** Does not throw on 4xx/5xx: it returns the response so it can be asserted. */
    @Autowired
    private TestRestTemplate rest;

    /**
     * Sends every request in this class as ADMIN.
     *
     * <p>These tests are about the API's behaviour, not about who may call it:
     * that is the subject of {@link SecurityE2ETest}. Authenticating once here,
     * with the role that can do everything, keeps each test showing what it is
     * really testing instead of the plumbing of the header.</p>
     *
     * <p>It has to run before each method and not once for the whole class
     * because {@code @DirtiesContext} rebuilds the context — and with it the
     * {@code TestRestTemplate} — between methods.</p>
     */
    @BeforeEach
    void autenticarComoAdmin() {
        E2EAuth.autenticarComo(rest, E2EAuth.ADMIN);
    }

    private static final String CLOWNS_URL = "/api/clowns";
    private static final String COWS_URL = "/api/cows";
    private static final String OWNERS_URL = "/api/owners";

    /**
     * Creates a test owner and cow, returns the cow's id.
     *
     * <p>Two requests, not one: the owner has to exist before the cow can, and
     * both go through the API rather than the repository so the fixture takes
     * the same path as production code. The cow name is a parameter because
     * names are unique — tests that need two cows would collide otherwise.</p>
     */
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

    /**
     * The N to M resolved at creation time: the clown row and its
     * {@code clown_cow} row are written in the same transaction, from a list of
     * ids in the body.
     */
    @Test
    @Order(2)
    @DisplayName("POST /api/clowns — 201: creates clown with assigned cows")
    void createClown_withCows_returns201() {
        UUID cowId = createTestCow("Lola");
        ClownRequest request = new ClownRequest("Pennywise", "Terror", List.of(cowId));

        ResponseEntity<ClownResponse> response = rest.postForEntity(
                CLOWNS_URL, request, ClownResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // totalCows = 1 means the join row was really inserted: the response is
        // built from the entity after the flush, so a link that failed to
        // persist would show up as 0 here.
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
    // The block that matters most in this class. Linking and unlinking after the
    // fact, through the two sub-resource endpoints, with the rows actually
    // written to and deleted from clown_cow.

    /**
     * Creates the link between a clown and a cow that already exist separately.
     * Answers 200 with the updated clown rather than 201 — a single link has no
     * URL of its own, so there is no Location to point at.
     */
    @Test
    @Order(7)
    @DisplayName("POST /api/clowns/{id}/cows/{cowId} — 200: assigns cow")
    void assignCow_returns200() {
        UUID cowId = createTestCow("Lola");
        // The clown is born with no cows: the link is made afterwards, which is
        // what separates this test from the create-with-cowIds one above.
        ClownResponse clown = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, null), ClownResponse.class).getBody();

        // Null body: both ids travel in the path.
        ResponseEntity<ClownResponse> response = rest.postForEntity(
                CLOWNS_URL + "/" + clown.id() + "/cows/" + cowId,
                null, ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().totalCows());
    }

    /**
     * There is no unique constraint on {@code clown_cow} to fall back on, so
     * without the service's check a repeated call would either duplicate the row
     * or quietly do nothing while answering 200. The 409 is what makes the
     * outcome unambiguous to the caller.
     */
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

    /**
     * The only real DELETE in the project: the {@code clown_cow} row is removed
     * rather than flagged, because a join row means nothing once the link stops
     * being true. Both the clown and the cow survive untouched.
     *
     * <p>It answers 200 with the updated clown, not the 204 that deleting a clown
     * returns — there is still a resource left to describe.</p>
     */
    @Test
    @Order(9)
    @DisplayName("DELETE /api/clowns/{id}/cows/{cowId} — 200: unassigns cow")
    void unassignCow_returns200() {
        UUID cowId = createTestCow("Lola");
        // Born already linked, so there is something to undo.
        ClownResponse clown = rest.postForEntity(CLOWNS_URL,
                new ClownRequest("Pennywise", null, List.of(cowId)), ClownResponse.class).getBody();

        ResponseEntity<ClownResponse> response = rest.exchange(
                CLOWNS_URL + "/" + clown.id() + "/cows/" + cowId,
                HttpMethod.DELETE, null, ClownResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().totalCows());

        // The cow itself is untouched: unlinking is not deleting. This is what
        // separates the N to M from the owner cascade, where deleting the owner
        // does deactivate the cows.
        ResponseEntity<CowResponse> cow = rest.getForEntity(
                COWS_URL + "/" + cowId, CowResponse.class);
        assertEquals(HttpStatus.OK, cow.getStatusCode());
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
