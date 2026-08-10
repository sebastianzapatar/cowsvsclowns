package com.sebasmalparqueado.cowsvsclown.owner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link OwnerService}: the real service against the real
 * repositories and H2, and then the same operations entered through the
 * controller.
 *
 * <p>What is specific to this service is the <b>1 to N cascade</b>. An owner can
 * be created with its cows nested in the same request, and nobody saves those
 * cows explicitly: {@code ownerRepository.save(owner)} is the only call, and the
 * {@code cascade = ALL} on the {@code @OneToMany} is what makes Hibernate insert
 * the owner first, take its generated id, and then insert each cow with that id
 * already in {@code owner_id}.</p>
 *
 * <p>That whole chain is invisible to a mocked test: with a mocked repository,
 * {@code save()} returns whatever the test told it to and the cascade never
 * happens. The only way to know the cows were written — and written with the
 * right foreign key — is to save for real and read the {@code cows} table back,
 * which is what these tests do.</p>
 *
 * <p>See {@code ClownServiceIntegrationTest} for why the class is not
 * {@code @Transactional}, why the database is cleaned by hand and why this
 * family of tests runs on a database of its own.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:integrationdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class OwnerServiceIntegrationTest {

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private IOwnerRepository ownerRepository;

    /** Used to check that the cascade really reached the {@code cows} table. */
    @Autowired
    private ICowRepository cowRepository;

    @Autowired
    private IClownRepository clownRepository;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        // Same order as in the other two classes: clowns (and with them their
        // rows in clown_cow), then cows, then owners.
        clownRepository.deleteAll();
        cowRepository.deleteAll();
        ownerRepository.deleteAll();
    }

    // ==================== Service + repository ==========================

    @Nested
    @DisplayName("Service + repository")
    class ServiceAndRepository {

        @Test
        @DisplayName("create saves the nested cows by cascade with their owner_id")
        void create_cascadesCows() {
            OwnerResponse response = ownerService.create(new OwnerRequest(
                    "Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12),
                            new OwnerCowRequest("Margarita", 400, 9))));

            assertEquals(2, response.totalCows());

            // Two rows in a table nobody wrote to explicitly: this is the
            // cascade. And each one carries the owner_id, which is the part that
            // would fail if OwnerMapper used a plain add() instead of addCow().
            List<Cow> cows = cowRepository.findAllByOwnerIdAndActiveTrueOrderByNameAsc(response.id());
            assertEquals(2, cows.size());
            assertEquals("Lola", cows.getFirst().getName());
            assertEquals(response.id(), cows.getFirst().getOwner().getId());
        }

        @Test
        @DisplayName("create rejects two cows with the same name in one request")
        void create_repeatedCowNameInRequest_throwsConflict() {
            assertThrows(ConflictException.class, () -> ownerService.create(new OwnerRequest(
                    "Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12),
                            new OwnerCowRequest("lola", 400, 9)))));

            // Validated before saving so the answer is a readable 409. Left to
            // the database, the UNIQUE index on cows.name would trigger instead
            // and the client would get a message full of constraint names.
            assertEquals(0, ownerRepository.count());
            assertEquals(0, cowRepository.count());
        }

        @Test
        @DisplayName("create rejects a cow name that already exists in the database")
        void create_cowNameAlreadyTaken_throwsConflict() {
            ownerService.create(new OwnerRequest("Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12))));

            assertThrows(ConflictException.class, () -> ownerService.create(new OwnerRequest(
                    "Valentina", "Betancur",
                    List.of(new OwnerCowRequest("LOLA", 400, 9)))));

            // The second owner was never created either: the validation runs
            // before the save, so the request is rejected whole.
            assertEquals(1, ownerRepository.count());
            assertEquals(1, cowRepository.count());
        }

        @Test
        @DisplayName("create rejects a duplicate owner ignoring case")
        void create_duplicateOwner_throwsConflict() {
            ownerService.create(new OwnerRequest("Sebastián", "Zapata", null));

            assertThrows(ConflictException.class, () ->
                    ownerService.create(new OwnerRequest("SEBASTIÁN", "zapata", null)));

            assertEquals(1, ownerRepository.count());
        }

        /**
         * The contrast with the cow rule, and it is deliberate on both sides.
         *
         * <p>An owner's name is only checked against <b>active</b> owners, so
         * deactivating one frees the name. A cow's name is checked against every
         * row, active or not, because {@code cows.name} carries a UNIQUE index
         * and the index does not care about the flag. Two rules that look
         * inconsistent until you look at the schema.</p>
         */
        @Test
        @DisplayName("a soft-deleted owner frees its name")
        void create_nameOfSoftDeletedOwner_isAllowed() {
            OwnerResponse first = ownerService.create(
                    new OwnerRequest("Sebastián", "Zapata", null));
            ownerService.softDelete(first.id());

            OwnerResponse second = ownerService.create(
                    new OwnerRequest("Sebastián", "Zapata", null));

            assertNotEquals(first.id(), second.id());
            // Two rows with the same name: the old one deactivated, the new one
            // alive. There is no unique index on the owners table to stop it.
            assertEquals(2, ownerRepository.count());
        }

        @Test
        @DisplayName("update does not make the owner collide with itself")
        void update_sameNameAsItself_isAllowed() {
            OwnerResponse owner = ownerService.create(
                    new OwnerRequest("Sebastián", "Zapata", null));

            // Only the last name changes, the first name is resent unchanged.
            // Without the "AndIdNot" variant of the query, the owner would be
            // found as a duplicate of itself and this would be a 409.
            OwnerResponse updated = ownerService.update(
                    owner.id(), new OwnerUpdateRequest("Sebastián", "Zapata Ríos"));

            assertEquals("Sebastián Zapata Ríos", updated.fullName());
            assertEquals("Zapata Ríos",
                    ownerRepository.findById(owner.id()).orElseThrow().getLastName());
        }

        @Test
        @DisplayName("softDelete deactivates the owner and its cows without deleting rows")
        void softDelete_deactivatesOwnerAndCows() {
            OwnerResponse owner = ownerService.create(new OwnerRequest(
                    "Sebastián", "Zapata", List.of(new OwnerCowRequest("Lola", 450, 12))));

            ownerService.softDelete(owner.id());

            // Both rows survive...
            assertEquals(1, ownerRepository.count());
            assertEquals(1, cowRepository.count());
            // ...with their flags down. The cows go with the owner because
            // owner_id is NOT NULL: a cow cannot be left without one, so the
            // alternative would be transferring them first with
            // PATCH /api/cows/{id}/owner/{ownerId}.
            assertFalse(ownerRepository.findById(owner.id()).orElseThrow().isActive());
            assertFalse(cowRepository.findAll().getFirst().isActive());

            assertThrows(ResourceNotFoundException.class, () -> ownerService.getById(owner.id()));
            assertTrue(ownerService.getAll().isEmpty());
        }

        @Test
        @DisplayName("getAll also brings owners with no cows")
        void getAll_includesOwnersWithoutCows() {
            ownerService.create(new OwnerRequest("Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12))));
            ownerService.create(new OwnerRequest("Valentina", "Betancur", null));

            List<OwnerResponse> result = ownerService.getAll();

            // The query is a LEFT JOIN FETCH and not an INNER one, which is the
            // whole difference here: with an INNER, the owner with no cows would
            // silently vanish from the list.
            assertEquals(2, result.size());
            // Sorted by last name: Betancur before Zapata.
            assertEquals("Betancur", result.getFirst().lastName());
            assertEquals(0, result.getFirst().totalCows());
        }

        @Test
        @DisplayName("an inactive cow no longer counts in its owner's total")
        void getById_ignoresInactiveCows() {
            OwnerResponse owner = ownerService.create(new OwnerRequest(
                    "Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12),
                            new OwnerCowRequest("Margarita", 400, 9))));

            Cow lola = cowRepository.findByNameIgnoreCaseAndActiveTrue("Lola").orElseThrow();
            lola.setActive(false);
            cowRepository.save(lola);

            // The query still fetches both cows — the filter is not in the SQL —
            // and it is the mapper that leaves the inactive one out on the way
            // to the client.
            OwnerResponse result = ownerService.getById(owner.id());
            assertEquals(1, result.totalCows());
            assertEquals(List.of("Margarita"), result.cows());
        }

        @Test
        @DisplayName("getActiveEntityOrThrow returns the entity or a 404")
        void getActiveEntityOrThrow_bothPaths() {
            OwnerResponse owner = ownerService.create(
                    new OwnerRequest("Sebastián", "Zapata", null));

            // The one method that returns an entity instead of a DTO, because
            // CowService needs the JPA object to build the relationship.
            Owner found = ownerService.getActiveEntityOrThrow(owner.id());
            assertEquals("Sebastián Zapata", found.getFullName());

            assertThrows(ResourceNotFoundException.class,
                    () -> ownerService.getActiveEntityOrThrow(9999L));
        }
    }

    // ============= Controller + service + repository =====================

    @Nested
    @DisplayName("Controller + service + repository")
    class ControllerServiceAndRepository {

        @Test
        @DisplayName("POST /api/owners creates the owner and its cows in one request")
        void post_createsOwnerWithCows() throws Exception {
            OwnerRequest request = new OwnerRequest("Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12),
                            new OwnerCowRequest("Margarita", 400, 9)));

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fullName").value("Sebastián Zapata"))
                    .andExpect(jsonPath("$.totalCows").value(2));

            // One HTTP request, three rows: this is the cascade seen end to end.
            assertEquals(1, ownerRepository.count());
            assertEquals(2, cowRepository.count());
        }

        @Test
        @DisplayName("POST /api/owners with a nested cow without a name returns 400")
        void post_invalidNestedCow_returns400() throws Exception {
            OwnerRequest request = new OwnerRequest("Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("", 450, 12)));

            // The @Valid on the list inside OwnerRequest is what makes this a
            // 400. Without that cascading annotation, Spring would validate the
            // owner, ignore the cows, and the nameless cow would reach the
            // service.
            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, ownerRepository.count());
            assertEquals(0, cowRepository.count());
        }

        @Test
        @DisplayName("POST /api/owners twice returns 409 and leaves a single owner")
        void post_duplicate_returns409() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new OwnerRequest("Sebastián", "Zapata", null));

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());

            assertEquals(1, ownerRepository.count());
        }

        @Test
        @DisplayName("PATCH /api/owners/{id} persists the change")
        void patch_persistsChange() throws Exception {
            Long ownerId = createOwnerByApi();

            mockMvc.perform(patch("/api/owners/{id}", ownerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new OwnerUpdateRequest(null, "Zapata Ríos"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Sebastián Zapata Ríos"));

            assertEquals("Zapata Ríos",
                    ownerRepository.findById(ownerId).orElseThrow().getLastName());
        }

        @Test
        @DisplayName("DELETE /api/owners/{id} takes the cows down with it")
        void delete_softDeletesOwnerAndCows() throws Exception {
            Long ownerId = createOwnerByApi();

            mockMvc.perform(delete("/api/owners/{id}", ownerId))
                    .andExpect(status().isNoContent());

            assertFalse(ownerRepository.findById(ownerId).orElseThrow().isActive());
            assertFalse(cowRepository.findAll().getFirst().isActive());

            mockMvc.perform(get("/api/owners/{id}", ownerId))
                    .andExpect(status().isNotFound());
            // The cow disappears from its own endpoint too, without anyone
            // having touched the cows resource.
            mockMvc.perform(get("/api/cows"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        /** Creates the fixture owner with one cow through the API. */
        private Long createOwnerByApi() throws Exception {
            String body = mockMvc.perform(post("/api/owners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new OwnerRequest(
                                    "Sebastián", "Zapata",
                                    List.of(new OwnerCowRequest("Lola", 450, 12))))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readValue(body, OwnerResponse.class).id();
        }
    }
}
