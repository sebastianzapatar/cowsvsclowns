package com.sebasmalparqueado.cowsvsclown.cows.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
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
 * Integration tests for {@link CowService}: the real service against the real
 * repositories and H2, and then the same operations entered through the
 * controller. Nothing is mocked, not even {@code OwnerService}, which this
 * service depends on.
 *
 * <p>The cow is where the two relationships of the model meet, and each one
 * fails in a different way when it is only tested with mocks:</p>
 *
 * <ul>
 *   <li><b>1 to N with the owner.</b> The {@code owner_id} column lives in the
 *       {@code cows} table and is NOT NULL. A mock cannot tell whether the
 *       column ended up filled, only whether a setter was called.</li>
 *   <li><b>N to M with the clowns.</b> The rows go into {@code clown_cow},
 *       written from the clown's side even when the request came in through the
 *       cow. Only a real commit shows that detour actually works.</li>
 * </ul>
 *
 * <p>See {@code ClownServiceIntegrationTest} for why the class is not
 * {@code @Transactional}, why the cleanup is done by hand and why this family
 * of tests uses a database of its own: the short version is that a test
 * transaction would hide exactly the failures this level exists to catch, and
 * that the e2e tests drop the shared schema when their context is discarded.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:integrationdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class CowServiceIntegrationTest {

    @Autowired
    private CowService cowService;

    @Autowired
    private ICowRepository cowRepository;

    @Autowired
    private IClownRepository clownRepository;

    @Autowired
    private IOwnerRepository ownerRepository;

    /** To read {@code clown_cow}, the join table that has no entity. */
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Owner of the fixture: no cow can exist without one. */
    private Owner sebastian;

    /** Second owner, for the transfer tests. */
    private Owner valentina;

    @BeforeEach
    void cleanDatabaseAndPrepareFixture() {
        // Clowns first so their rows in clown_cow go with them, then the cows
        // they pointed at, and the owners last because the cows reference them.
        clownRepository.deleteAll();
        cowRepository.deleteAll();
        ownerRepository.deleteAll();

        sebastian = ownerRepository.save(Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build());
        valentina = ownerRepository.save(Owner.builder()
                .firstName("Valentina").lastName("Betancur").active(true).build());
    }

    // ==================== Service + repository ==========================

    @Nested
    @DisplayName("Service + repository")
    class ServiceAndRepository {

        @Test
        @DisplayName("create fills owner_id: the 1 to N really lands in the column")
        void create_persistsOwnerForeignKey() {
            CowResponse response = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            assertEquals("Sebastián Zapata", response.owner().fullName());

            // Read back from the database, in a new transaction. The foreign key
            // is the whole point: owner_id is NOT NULL, so if the service had
            // built the relationship the wrong way round the insert would have
            // failed here and not in the response.
            Cow persisted = cowRepository.findActiveWithRelationsById(response.id()).orElseThrow();
            assertEquals(sebastian.getId(), persisted.getOwner().getId());
        }

        @Test
        @DisplayName("create with clownIds writes the rows in clown_cow")
        void create_withClowns_writesJoinRows() {
            Clown pennywise = clownRepository.save(Clown.builder()
                    .name("Pennywise").description("Terror").active(true).build());

            CowResponse response = cowService.create(new CowRequest(
                    "Lola", 450, 12, sebastian.getId(), List.of(pennywise.getId())));

            assertEquals(1, response.clowns().size());
            // The request arrived through the cow, but the row can only be
            // written from the clown, which is the owning side. That detour
            // inside assignClowns() is what this assertion covers.
            assertEquals(1, countJoinRows(pennywise.getId(), response.id()));
        }

        /**
         * The clearest rollback in the project: by the time the failure happens
         * the cow has already been saved, so something really has to be undone.
         */
        @Test
        @DisplayName("create with an unknown clown rolls back the cow already saved")
        void create_unknownClown_rollsBackTheCow() {
            assertThrows(ResourceNotFoundException.class, () -> cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(),
                            List.of(UUID.randomUUID()))));

            // create() saves the cow first and assigns the clowns afterwards,
            // because the cow needs its generated id before it can be referenced
            // in the join table. So when the clown lookup fails there is already
            // an insert in flight, and only the @Transactional undoes it.
            assertEquals(0, cowRepository.count());
        }

        @Test
        @DisplayName("create with an unknown owner is a 404 and saves nothing")
        void create_unknownOwner_savesNothing() {
            assertThrows(ResourceNotFoundException.class, () ->
                    cowService.create(new CowRequest("Lola", 450, 12, 9999L, null)));

            assertEquals(0, cowRepository.count());
        }

        /**
         * The rule that only makes sense with a real database behind it.
         *
         * <p>{@code existsByNameIgnoreCase} does not filter by {@code active} on
         * purpose: the UNIQUE index on {@code cows.name} counts soft-deleted rows
         * too. If the check ignored them, the service would let the name through
         * and the insert would then blow up against the constraint with a
         * message nobody can read.</p>
         */
        @Test
        @DisplayName("a soft-deleted cow still holds its name")
        void create_nameOfSoftDeletedCow_throwsConflict() {
            CowResponse lola = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));
            cowService.softDelete(lola.id());

            assertThrows(ConflictException.class, () ->
                    cowService.create(new CowRequest("lola", 400, 10, sebastian.getId(), null)));

            // One row, the deactivated one: the second create never got in.
            assertEquals(1, cowRepository.count());
        }

        @Test
        @DisplayName("update only touches the fields that arrive")
        void update_onlyTouchesGivenFields() {
            CowResponse lola = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            cowService.update(lola.id(), new CowUpdateRequest(null, 470, null));

            Cow persisted = cowRepository.findById(lola.id()).orElseThrow();
            assertEquals(470, persisted.getWeight());
            // The two nulls mean "do not touch", which is what separates PATCH
            // from PUT. With int instead of Integer in the DTO they would have
            // arrived as 0 and wiped these values.
            assertEquals("Lola", persisted.getName());
            assertEquals(12, persisted.getMilkperday());
        }

        /**
         * Guards a trap documented in the service: the previous owner's list has
         * to be cleaned with a plain {@code remove} and never with
         * {@code owner.removeCow()}, because that helper also sets the cow's
         * owner to null and the relationship has {@code orphanRemoval = true} —
         * Hibernate would read that as "this cow is an orphan now" and delete it
         * from the database on commit.
         */
        @Test
        @DisplayName("changeOwner moves the cow without deleting it")
        void changeOwner_movesWithoutDeleting() {
            CowResponse lola = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            CowResponse moved = cowService.changeOwner(lola.id(), valentina.getId());

            assertEquals("Valentina Betancur", moved.owner().fullName());

            // The cow is still there. If the transfer had gone through
            // removeCow(), this findById would come back empty and the test
            // would fail exactly where the bug would be.
            Cow persisted = cowRepository.findActiveWithRelationsById(lola.id()).orElseThrow();
            assertEquals(valentina.getId(), persisted.getOwner().getId());
            assertEquals(1, cowRepository.count());

            // And it moved: it now shows up under the new owner and no longer
            // under the old one.
            assertEquals(1, cowService.getByOwner(valentina.getId()).size());
            assertTrue(cowService.getByOwner(sebastian.getId()).isEmpty());
        }

        @Test
        @DisplayName("changeOwner to the same owner is a conflict")
        void changeOwner_sameOwner_throwsConflict() {
            CowResponse lola = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            assertThrows(ConflictException.class,
                    () -> cowService.changeOwner(lola.id(), sebastian.getId()));
        }

        @Test
        @DisplayName("softDelete keeps the row and hides it from every query")
        void softDelete_keepsRowAndHidesIt() {
            CowResponse lola = cowService.create(
                    new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            cowService.softDelete(lola.id());

            // The row survives with the flag down...
            Cow persisted = cowRepository.findById(lola.id()).orElseThrow();
            assertFalse(persisted.isActive());
            // ...and disappears from the three read paths, each of which filters
            // by active in its own way: the JPQL with JOIN FETCH, the derived
            // query by owner, and the finder by id.
            assertTrue(cowService.getCows().isEmpty());
            assertTrue(cowService.getByOwner(sebastian.getId()).isEmpty());
            assertThrows(ResourceNotFoundException.class, () -> cowService.getById(lola.id()));
        }

        @Test
        @DisplayName("getByName resolves through the native SQL query")
        void getByName_usesNativeQuery() {
            cowService.create(new CowRequest("Lola", 450, 12, sebastian.getId(), null));

            // findByNameSQL is written in raw SQL, with real table and column
            // names and a LIMIT: nothing in it is validated at compile time, so
            // it can only be checked by running it against a database.
            CowResponse result = cowService.getByName("lola");

            assertEquals("Lola", result.name());
            assertThrows(ResourceNotFoundException.class, () -> cowService.getByName("Nadie"));
        }

        @Test
        @DisplayName("getByOwner throws 404 if the owner does not exist")
        void getByOwner_unknownOwner_throwsNotFound() {
            // The check is delegated to the real OwnerService, which is the only
            // service-to-service call in the project. Here it is the real one.
            assertThrows(ResourceNotFoundException.class, () -> cowService.getByOwner(9999L));
        }
    }

    // ============= Controller + service + repository =====================

    @Nested
    @DisplayName("Controller + service + repository")
    class ControllerServiceAndRepository {

        @Test
        @DisplayName("POST /api/cows creates the cow with its owner")
        void post_createsCowWithOwner() throws Exception {
            CowRequest request = new CowRequest("Lola", 450, 12, sebastian.getId(), null);

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Lola"))
                    .andExpect(jsonPath("$.owner.fullName").value("Sebastián Zapata"));

            Cow persisted = cowRepository.findAllActiveWithRelations().getFirst();
            assertEquals(sebastian.getId(), persisted.getOwner().getId());
        }

        @Test
        @DisplayName("POST /api/cows with an unknown owner returns 404 and saves nothing")
        void post_unknownOwner_returns404() throws Exception {
            CowRequest request = new CowRequest("Lola", 450, 12, 9999L, null);

            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            assertEquals(0, cowRepository.count());
        }

        @Test
        @DisplayName("POST /api/cows with weight 0 returns 400 before reaching the service")
        void post_invalidWeight_returns400() throws Exception {
            CowRequest request = new CowRequest("Lola", 0, 12, sebastian.getId(), null);

            // @Min(1) on the DTO. The body never reaches the controller method,
            // so nothing gets as far as the database.
            mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, cowRepository.count());
        }

        @Test
        @DisplayName("PATCH /api/cows/{id}/owner/{ownerId} transfers the cow")
        void patchOwner_transfersTheCow() throws Exception {
            UUID cowId = createCowByApi("Lola");

            mockMvc.perform(patch("/api/cows/{id}/owner/{ownerId}", cowId, valentina.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.owner.fullName").value("Valentina Betancur"));

            Cow persisted = cowRepository.findActiveWithRelationsById(cowId).orElseThrow();
            assertEquals(valentina.getId(), persisted.getOwner().getId());
        }

        @Test
        @DisplayName("GET /api/cows/search?name=... answers with the native query")
        void getSearch_findsByNameIgnoringCase() throws Exception {
            createCowByApi("Lola");

            mockMvc.perform(get("/api/cows/search").param("name", "LOLA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Lola"));

            mockMvc.perform(get("/api/cows/search").param("name", "Nadie"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/cows/owner/{ownerId} lists only that owner's cows")
        void getByOwner_listsOnlyItsOwn() throws Exception {
            createCowByApi("Lola");
            UUID margarita = createCowByApi("Margarita");
            mockMvc.perform(patch("/api/cows/{id}/owner/{ownerId}", margarita, valentina.getId()))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/cows/owner/{ownerId}", sebastian.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Lola"));
        }

        @Test
        @DisplayName("DELETE /api/cows/{id} deactivates the row and then it is a 404")
        void delete_softDeletesAndThenNotFound() throws Exception {
            UUID cowId = createCowByApi("Lola");

            mockMvc.perform(delete("/api/cows/{id}", cowId))
                    .andExpect(status().isNoContent());

            assertEquals(1, cowRepository.count());
            assertFalse(cowRepository.findById(cowId).orElseThrow().isActive());

            mockMvc.perform(get("/api/cows/{id}", cowId))
                    .andExpect(status().isNotFound());
        }

        /** Creates a cow of {@code sebastian} through the API and returns its id. */
        private UUID createCowByApi(String name) throws Exception {
            String body = mockMvc.perform(post("/api/cows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CowRequest(name, 450, 12, sebastian.getId(), null))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readValue(body, CowResponse.class).id();
        }
    }

    // ============================== Helpers ===============================

    /** Rows in {@code clown_cow} for that pair: 0 or 1, since the pair is unique. */
    private int countJoinRows(UUID clownId, UUID cowId) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clown_cow WHERE clown_id = ? AND cow_id = ?",
                Integer.class, clownId, cowId);
        return total == null ? 0 : total;
    }
}
