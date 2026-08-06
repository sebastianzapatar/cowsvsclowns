package com.sebasmalparqueado.cowsvsclown.cows.repository;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cow repository integration tests.
 *
 * <p>This is the repository with the most query variety in the project, and the
 * one where running them for real pays off the most: it mixes derived queries
 * (built by Spring Data from the method name), JPQL with {@code JOIN FETCH}, and
 * one <b>native SQL</b> query. The native one is the interesting case — it is
 * plain text that the compiler never checks, so a typo in a column name only
 * surfaces when the query actually runs.</p>
 *
 * <p>Runs against in-memory H2 in PostgreSQL compatibility mode. Worth knowing
 * where that abstraction leaks: H2 accepts most of Postgres' dialect, but it is
 * not Postgres. Native SQL leaning on a Postgres-only feature could pass here
 * and fail in production. For the queries in this project the equivalence
 * holds.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class CowRepositoryIntegrationTest {

    @Autowired
    private ICowRepository cowRepository;

    /** Builds the fixture through a different door than the one under test. */
    @Autowired
    private TestEntityManager em;

    private Owner owner;
    private Cow activeCow;
    private Cow inactiveCow;

    @BeforeEach
    void setUp() {
        // An owner is mandatory: cows.owner_id is NOT NULL, so there is no such
        // thing as an orphan cow to build a fixture with.
        owner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        em.persistAndFlush(owner);

        activeCow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).owner(owner).build();
        em.persistAndFlush(activeCow);

        // The soft-deleted counterpart. Every finder here filters by
        // active = true; without this row those filters would be untested.
        inactiveCow = Cow.builder()
                .name("Muerta").weight(300).milkperday(0).active(false).owner(owner).build();
        em.persistAndFlush(inactiveCow);

        // Drops the first-level cache so the finders below are forced to hit the
        // database instead of being handed the instances just persisted.
        em.clear();
    }

    @Test
    @DisplayName("findAllActiveWithRelations returns only active cows with owner")
    void findAllActiveWithRelations_returnsActive() {
        List<Cow> result = cowRepository.findAllActiveWithRelations();

        assertEquals(1, result.size());
        assertEquals("Lola", result.getFirst().getName());
        // The owner arrives already loaded thanks to the JOIN FETCH. The
        // relationship is LAZY, so this assertion is what stands between the
        // API and either a LazyInitializationException or an N+1 storm.
        assertNotNull(result.getFirst().getOwner());
    }

    @Test
    @DisplayName("findActiveWithRelationsById finds the active cow")
    void findActiveWithRelationsById_found() {
        Optional<Cow> result = cowRepository.findActiveWithRelationsById(activeCow.getId());

        assertTrue(result.isPresent());
        assertEquals("Lola", result.get().getName());
    }

    @Test
    @DisplayName("findByNameSQL searches by name case-insensitive (native query)")
    void findByNameSQL_caseInsensitive() {
        // The native SQL query. Searched in lowercase against a fixture stored
        // as "Lola": this passes only if the LOWER() in the SQL is right. Being
        // a raw string, this test is the only thing that checks it at all.
        Optional<Cow> result = cowRepository.findByNameSQL("lola");

        assertTrue(result.isPresent());
        assertEquals("Lola", result.get().getName());
    }

    @Test
    @DisplayName("findByNameSQL does not find inactive cows")
    void findByNameSQL_inactiveNotFound() {
        // Searched by its exact name, so the only reason to come back empty is
        // the active = true filter inside the native SQL doing its job.
        Optional<Cow> result = cowRepository.findByNameSQL("Muerta");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("existsByNameIgnoreCase detects duplicate")
    void existsByNameIgnoreCase_works() {
        // Backs the uniqueness rule on cow names. Both directions are asserted:
        // a name-only check would pass against a method that always said true.
        assertTrue(cowRepository.existsByNameIgnoreCase("lola"));
        assertFalse(cowRepository.existsByNameIgnoreCase("NoExiste"));
    }

    @Test
    @DisplayName("findAllByOwnerIdAndActiveTrueOrderByNameAsc returns owner's cows")
    void findByOwnerId_returnsOnlyActive() {
        // The N side of the 1 to N relationship: given an owner, their cows.
        // Both cows in the fixture belong to this owner, so getting exactly one
        // back proves the active filter is applied and not just the join.
        List<Cow> result = cowRepository
                .findAllByOwnerIdAndActiveTrueOrderByNameAsc(owner.getId());

        assertEquals(1, result.size());
        assertEquals("Lola", result.getFirst().getName());
    }
}
