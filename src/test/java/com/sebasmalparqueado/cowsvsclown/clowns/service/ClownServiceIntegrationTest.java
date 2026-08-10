package com.sebasmalparqueado.cowsvsclown.clowns.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import com.sebasmalparqueado.cowsvsclown.owner.repository.IOwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ClownService}: the service running against the
 * <b>real repository</b> and, in the second block, reached through the
 * <b>real controller</b>. Nothing here is mocked.
 *
 * <p>This is the level the suite was missing. Each of the other test types
 * cuts the application at a different point:</p>
 *
 * <table>
 *   <caption>What each level of the suite proves</caption>
 *   <tr><th>Test</th><th>What is real</th><th>What it cannot see</th></tr>
 *   <tr>
 *     <td>{@code ClownServiceTest} (unit)</td>
 *     <td>The service logic</td>
 *     <td>Whether the repository query exists, whether the row is written</td>
 *   </tr>
 *   <tr>
 *     <td>{@code ClownControllerIntegrationTest} ({@code @WebMvcTest})</td>
 *     <td>Routing, JSON, {@code @Valid}, error handler</td>
 *     <td>Everything below the service, which is a mock</td>
 *   </tr>
 *   <tr>
 *     <td>{@code ClownRepositoryIntegrationTest} ({@code @DataJpaTest})</td>
 *     <td>The JPQL queries</td>
 *     <td>Whether the service calls them and in what order</td>
 *   </tr>
 *   <tr>
 *     <td><b>this class</b></td>
 *     <td>Service + repository + H2, and controller on top</td>
 *     <td>The HTTP transport itself (that is the e2e's job)</td>
 *   </tr>
 * </table>
 *
 * <p>Because the pieces are real, the assertions can be about the
 * <b>database</b> and not about a mock: after every operation the rows are read
 * back to check what actually got persisted. That is what makes these tests
 * worth having for an N to M relationship — a mocked test can only show that an
 * in-memory list changed, never that the {@code clown_cow} row exists.</p>
 *
 * <p><b>Why there is no {@code @Transactional} on the class.</b> Adding it would
 * wrap every test in a transaction that rolls back at the end, which is
 * convenient but would also make the service run <i>inside</i> the test's
 * transaction. Then a missing commit, a rollback that does not happen or a lazy
 * collection read outside its session would all go unnoticed, because the single
 * open session would cover them up. Here every service call opens and commits
 * its own transaction, exactly as in production, and the state is cleaned by
 * hand in {@link #cleanDatabaseAndPrepareFixture()}.</p>
 *
 * <p><b>Why its own database.</b> The {@code test} profile points every test at
 * {@code jdbc:h2:mem:testdb}, and H2 keys an in-memory database by that URL, so
 * whoever names it shares it. The e2e tests carry
 * {@code @DirtiesContext(AFTER_EACH_TEST_METHOD)}: their context is closed after
 * every method, and closing it makes {@code ddl-auto: create-drop} drop the
 * schema on the way out. The context of this class is cached and stays alive
 * across that, so it would keep pointing at a database whose tables no longer
 * exist and fail with "table not found" — or not, depending on the order Gradle
 * happened to pick. The {@code @TestPropertySource} moves this family to a
 * database of its own, which nobody else opens or drops. The three
 * {@code *ServiceIntegrationTest} classes declare the same URL on purpose: same
 * configuration means Spring reuses one context for all three instead of
 * booting it three times.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:integrationdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class ClownServiceIntegrationTest {

    /** The real service, with its real repositories injected by Spring. */
    @Autowired
    private ClownService clownService;

    @Autowired
    private IClownRepository clownRepository;

    @Autowired
    private ICowRepository cowRepository;

    @Autowired
    private IOwnerRepository ownerRepository;

    /**
     * Used to read the {@code clown_cow} join table directly in SQL.
     *
     * <p>That table has no entity of its own — Hibernate maintains it from the
     * {@code @JoinTable} declared on {@link Clown} — so plain SQL is the only
     * way to assert on it without asking JPA, which is precisely the layer
     * under test.</p>
     */
    @Autowired
    private JdbcTemplate jdbc;

    /** Entry point of the web layer without opening a real socket. */
    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Cow available to every test: a clown needs something to be linked to. */
    private Cow lola;

    /**
     * Leaves the database empty and creates the shared fixture.
     *
     * <p>The order of the deletes is not arbitrary: {@code clown_cow} has
     * foreign keys to both tables. Deleting the clowns first also removes their
     * join rows (Hibernate deletes the collection rows before the owning
     * entity), so the cows are then free to go, and the owners last because the
     * cows point at them through {@code owner_id}.</p>
     */
    @BeforeEach
    void cleanDatabaseAndPrepareFixture() {
        clownRepository.deleteAll();
        cowRepository.deleteAll();
        ownerRepository.deleteAll();

        Owner owner = ownerRepository.save(Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build());

        lola = cowRepository.save(Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).owner(owner).build());
    }

    // ==================== Service + repository ==========================

    /**
     * The service called directly, with no web layer in the way. What is being
     * checked is the pair service/repository: that the queries the service asks
     * for exist, return what it expects, and that the writes reach the tables.
     */
    @Nested
    @DisplayName("Service + repository")
    class ServiceAndRepository {

        @Test
        @DisplayName("create persists the clown and the row in clown_cow")
        void create_persistsClownAndJoinRow() {
            ClownResponse response = clownService.create(
                    new ClownRequest("Pennywise", "Terror", List.of(lola.getId())));

            // 1. What the service answered.
            assertEquals("Pennywise", response.name());
            assertEquals(1, response.totalCows());

            // 2. What ended up in the clowns table. The read goes through the
            //    repository again, in a new transaction, so what is asserted is
            //    the committed row and not the object still in memory.
            Clown persisted = clownRepository.findActiveWithCowsById(response.id()).orElseThrow();
            assertEquals("Pennywise", persisted.getName());
            assertTrue(persisted.isActive());

            // 3. What ended up in the join table. This is the assertion the unit
            //    test cannot make: there the collection was updated in memory,
            //    here the row exists in the database.
            assertEquals(1, countJoinRows(response.id(), lola.getId()));
        }

        @Test
        @DisplayName("create with a cow that does not exist saves nothing")
        void create_unknownCow_savesNothing() {
            UUID missingCow = UUID.randomUUID();

            assertThrows(ResourceNotFoundException.class, () ->
                    clownService.create(new ClownRequest("Pennywise", "Terror", List.of(missingCow))));

            // The name was free and the entity was already built when the cow
            // lookup failed. The @Transactional is what guarantees that the
            // half-done work leaves no trace.
            assertEquals(0, clownRepository.count());
        }

        @Test
        @DisplayName("create rejects a duplicate name ignoring case")
        void create_duplicateName_throwsConflict() {
            clownService.create(new ClownRequest("Pennywise", "Terror", null));

            // Lowercase on purpose: the check is existsByNameIgnoreCase, and the
            // clowns.name column is UNIQUE. If the service let this through, the
            // failure would be a DataIntegrityViolationException from the
            // database instead of a clean 409.
            assertThrows(ConflictException.class, () ->
                    clownService.create(new ClownRequest("pennywise", "Otro", null)));

            assertEquals(1, clownRepository.count());
        }

        @Test
        @DisplayName("getAll brings the clown with its cows in a single query")
        void getAll_returnsClownWithItsCows() {
            Cow margarita = cowRepository.save(Cow.builder()
                    .name("Margarita").weight(400).milkperday(9).active(true)
                    .owner(lola.getOwner()).build());

            clownService.create(new ClownRequest(
                    "Pennywise", "Terror", List.of(lola.getId(), margarita.getId())));

            List<ClownResponse> result = clownService.getAll();

            // One clown with two cows, and not two rows of the same clown: the
            // JOIN FETCH multiplies the rows in SQL and Hibernate collapses them
            // when building the entities. This is what the repository's javadoc
            // means by "DISTINCT is not necessary".
            assertEquals(1, result.size());
            assertEquals(2, result.getFirst().totalCows());
        }

        @Test
        @DisplayName("getByCow reads the N to M from the cow's end")
        void getByCow_returnsClownsOfTheCow() {
            clownService.create(new ClownRequest("Pennywise", "Terror", List.of(lola.getId())));

            List<ClownResponse> result = clownService.getByCow(lola.getId());

            assertEquals(1, result.size());
            assertEquals("Pennywise", result.getFirst().name());
        }

        @Test
        @DisplayName("getByCow throws 404 if the cow does not exist")
        void getByCow_unknownCow_throwsNotFound() {
            // An empty list would be a valid answer for a cow nobody looks
            // after, so the cow has to be validated first for the two cases to
            // be distinguishable by the caller.
            assertThrows(ResourceNotFoundException.class,
                    () -> clownService.getByCow(UUID.randomUUID()));
        }

        @Test
        @DisplayName("update persists the new name")
        void update_persistsNewName() {
            ClownResponse created = clownService.create(
                    new ClownRequest("Pennywise", "Terror", null));

            clownService.update(created.id(), new ClownUpdateRequest("Bozo", null));

            Clown persisted = clownRepository.findById(created.id()).orElseThrow();
            assertEquals("Bozo", persisted.getName());
            // The description was sent as null, which in PATCH semantics means
            // "do not touch" and not "set to null".
            assertEquals("Terror", persisted.getDescription());
        }
    }

    // ============================== N to M ==============================

    /**
     * The link between clown and cow, from creation to destruction, checked
     * against the {@code clown_cow} table each time.
     *
     * <p>Every operation lives on the clown's side because {@link Clown} is the
     * owning side of the relationship: it declares the {@code @JoinTable}, and
     * Hibernate only writes join rows from there. These tests are what proves
     * that claim instead of just repeating it.</p>
     */
    @Nested
    @DisplayName("N to M against the clown_cow table")
    class NToM {

        private UUID clownId;

        @BeforeEach
        void createClown() {
            clownId = clownService.create(new ClownRequest("Pennywise", "Terror", null)).id();
        }

        @Test
        @DisplayName("assignCow inserts exactly one row")
        void assignCow_insertsRow() {
            assertEquals(0, countJoinRows(clownId, lola.getId()));

            ClownResponse response = clownService.assignCow(clownId, lola.getId());

            assertEquals(1, response.totalCows());
            assertEquals(1, countJoinRows(clownId, lola.getId()));
        }

        @Test
        @DisplayName("assigning twice is a conflict and does not duplicate the row")
        void assignCow_twice_throwsConflictAndKeepsOneRow() {
            clownService.assignCow(clownId, lola.getId());

            assertThrows(ConflictException.class,
                    () -> clownService.assignCow(clownId, lola.getId()));

            // Two reasons to assert the count and not just the exception: the
            // pair (clown_id, cow_id) is unique in the table, so a second insert
            // would fail with a database error rather than a 409, and a service
            // that silently ignored the repeat would leave the caller unable to
            // tell what happened.
            assertEquals(1, countJoinRows(clownId, lola.getId()));
        }

        @Test
        @DisplayName("unassignCow deletes the row and leaves both entities alive")
        void unassignCow_deletesRowOnly() {
            clownService.assignCow(clownId, lola.getId());

            clownService.unassignCow(clownId, lola.getId());

            // The one physical delete in the project. The join row is gone...
            assertEquals(0, countJoinRows(clownId, lola.getId()));
            // ...and neither the clown nor the cow was touched: the row only
            // meant "this clown looks after this cow", nothing else.
            assertTrue(clownRepository.findById(clownId).orElseThrow().isActive());
            assertTrue(cowRepository.findById(lola.getId()).orElseThrow().isActive());
        }

        @Test
        @DisplayName("soft-deleting the clown keeps the join row as history")
        void softDelete_keepsJoinRow() {
            clownService.assignCow(clownId, lola.getId());

            clownService.softDelete(clownId);

            // The clown row survives with the flag down...
            assertFalse(clownRepository.findById(clownId).orElseThrow().isActive());
            // ...the link survives too, because it is the record of who looked
            // after that cow...
            assertEquals(1, countJoinRows(clownId, lola.getId()));
            // ...and still the clown disappears from every read, because all the
            // queries filter by active. Deleting the join rows was never needed.
            assertTrue(clownService.getAll().isEmpty());
            assertTrue(clownService.getByCow(lola.getId()).isEmpty());
        }

        @Test
        @DisplayName("soft-deleting the cow hides it from the clown but keeps the link")
        void softDeletedCow_disappearsFromResponseButKeepsJoinRow() {
            clownService.assignCow(clownId, lola.getId());

            // Soft delete straight through the repository: what is under test
            // here is how the clown reacts, not the cow service.
            lola.setActive(false);
            cowRepository.save(lola);

            // The mapper filters inactive cows on the way out, so the clown
            // reports zero cows...
            assertEquals(0, clownService.getById(clownId).totalCows());
            // ...while the row is still there. Nothing was torn down; the filter
            // in the mapper is what makes the link stop being visible.
            assertEquals(1, countJoinRows(clownId, lola.getId()));
        }
    }

    // ============= Controller + service + repository =====================

    /**
     * The same operations, now entered through the controller with MockMvc: the
     * request crosses routing, JSON binding, {@code @Valid}, the service, the
     * repository and H2, and comes back serialized.
     *
     * <p>Two things are asserted on every test — the HTTP response <b>and</b>
     * the state of the database — because that pair is exactly what neither of
     * the two existing slice tests can check on its own.</p>
     */
    @Nested
    @DisplayName("Controller + service + repository")
    class ControllerServiceAndRepository {

        @Test
        @DisplayName("POST /api/clowns creates the clown and its link")
        void post_createsClownWithCow() throws Exception {
            ClownRequest request = new ClownRequest("Pennywise", "Terror", List.of(lola.getId()));

            mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Pennywise"))
                    .andExpect(jsonPath("$.totalCows").value(1))
                    .andExpect(jsonPath("$.cows[0].name").value("Lola"));

            Clown persisted = clownRepository.findAllActiveWithCows().getFirst();
            assertEquals("Pennywise", persisted.getName());
            assertEquals(1, countJoinRows(persisted.getId(), lola.getId()));
        }

        @Test
        @DisplayName("POST /api/clowns with an unknown cow returns 404 and saves nothing")
        void post_unknownCow_returns404AndSavesNothing() throws Exception {
            ClownRequest request = new ClownRequest(
                    "Pennywise", "Terror", List.of(UUID.randomUUID()));

            mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            // The interesting half of this test: the exception travelled from
            // the service to the GlobalExceptionHandler and became a 404, and on
            // the way the transaction rolled back. Neither slice test can see
            // both halves.
            assertEquals(0, clownRepository.count());
        }

        @Test
        @DisplayName("POST /api/clowns with a blank name returns 400 before touching the database")
        void post_invalidName_returns400() throws Exception {
            ClownRequest request = new ClownRequest("", "Terror", null);

            mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // @Valid rejects the body before the controller method runs, so the
            // service is never called.
            assertEquals(0, clownRepository.count());
        }

        @Test
        @DisplayName("POST /api/clowns/{id}/cows/{cowId} assigns and repeats as 409")
        void postAssign_thenConflict() throws Exception {
            UUID clownId = createClownByApi("Pennywise");

            mockMvc.perform(post("/api/clowns/{id}/cows/{cowId}", clownId, lola.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCows").value(1));

            assertEquals(1, countJoinRows(clownId, lola.getId()));

            mockMvc.perform(post("/api/clowns/{id}/cows/{cowId}", clownId, lola.getId()))
                    .andExpect(status().isConflict());

            assertEquals(1, countJoinRows(clownId, lola.getId()));
        }

        @Test
        @DisplayName("DELETE /api/clowns/{id}/cows/{cowId} removes only the link")
        void deleteAssignment_removesOnlyTheLink() throws Exception {
            UUID clownId = createClownByApi("Pennywise");
            mockMvc.perform(post("/api/clowns/{id}/cows/{cowId}", clownId, lola.getId()))
                    .andExpect(status().isOk());

            // 200 with the updated clown and not 204: unassigning leaves a clown
            // whose new state the caller usually wants to see.
            mockMvc.perform(delete("/api/clowns/{id}/cows/{cowId}", clownId, lola.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCows").value(0));

            assertEquals(0, countJoinRows(clownId, lola.getId()));
            assertTrue(cowRepository.findById(lola.getId()).orElseThrow().isActive());
        }

        @Test
        @DisplayName("DELETE /api/clowns/{id} deactivates the clown and then it is a 404")
        void delete_softDeletesAndThenNotFound() throws Exception {
            UUID clownId = createClownByApi("Pennywise");

            mockMvc.perform(delete("/api/clowns/{id}", clownId))
                    .andExpect(status().isNoContent());

            // The row is still there, only with the flag down: that is what
            // makes this a logical delete and not a DELETE.
            assertEquals(1, clownRepository.count());
            assertFalse(clownRepository.findById(clownId).orElseThrow().isActive());

            mockMvc.perform(get("/api/clowns/{id}", clownId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PATCH /api/clowns/{id} with a taken name returns 409 and changes nothing")
        void patch_duplicateName_returns409() throws Exception {
            UUID pennywise = createClownByApi("Pennywise");
            createClownByApi("Bozo");

            mockMvc.perform(patch("/api/clowns/{id}", pennywise)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ClownUpdateRequest("bozo", null))))
                    .andExpect(status().isConflict());

            assertEquals("Pennywise",
                    clownRepository.findById(pennywise).orElseThrow().getName());
        }

        /** Creates a clown through the API and returns its id. */
        private UUID createClownByApi(String name) throws Exception {
            String body = mockMvc.perform(post("/api/clowns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ClownRequest(name, "Test", null))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readValue(body, ClownResponse.class).id();
        }
    }

    // ============================== Helpers ===============================

    /**
     * Rows in {@code clown_cow} for that pair: 0 or 1, since the pair is unique.
     *
     * <p>Deliberately in plain SQL. Asking JPA whether the link exists would
     * mean trusting the very mapping under test, and a stale first-level cache
     * could answer with what is in memory instead of what is in the table.</p>
     */
    private int countJoinRows(UUID clownId, UUID cowId) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clown_cow WHERE clown_id = ? AND cow_id = ?",
                Integer.class, clownId, cowId);
        return total == null ? 0 : total;
    }
}
