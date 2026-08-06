package com.sebasmalparqueado.cowsvsclown.clowns.repository;

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
 * Clown repository integration tests.
 *
 * <p>What makes this repository different from the other two: its relationship
 * to {@code Cow} is <b>N to M</b>, resolved through the {@code clown_cow} join
 * table. That table has no entity of its own — Hibernate maintains it from the
 * {@code @JoinTable} declared on {@code Clown}. So there is no way to check it
 * by reading a mapped class; the only way to know the join is wired correctly is
 * to persist both sides and query across it, which is what these tests do.</p>
 *
 * <p>{@code Clown} is the owning side of the relationship (it declares the
 * {@code @JoinTable}) and {@code Cow} mirrors it with {@code mappedBy}. That
 * matters for the fixture: the link has to be created from the clown, because
 * Hibernate only writes the join rows for the owning side.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class ClownRepositoryIntegrationTest {

    @Autowired
    private IClownRepository clownRepository;

    /** Builds the fixture through a different door than the one under test. */
    @Autowired
    private TestEntityManager em;

    private Clown activeClown;
    private Clown inactiveClown;
    private Cow cow;

    @BeforeEach
    void setUp() {
        // Owner first: the cow cannot exist without one (owner_id is NOT NULL),
        // and the cow is needed to have something to link the clown to.
        Owner owner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        em.persistAndFlush(owner);

        cow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).owner(owner).build();
        em.persistAndFlush(cow);

        activeClown = Clown.builder()
                .name("Pennywise").description("Terror").active(true).build();
        // The link is made from the clown because it is the owning side of the
        // N to M. Doing it from the cow would leave the clown_cow row unwritten
        // and every query below would come back empty.
        activeClown.addCow(cow);
        em.persistAndFlush(activeClown);

        // Soft-deleted clown, with no cows: it is the control for the
        // active = true filter in the finders.
        inactiveClown = Clown.builder()
                .name("Bozo").description("Inactivo").active(false).build();
        em.persistAndFlush(inactiveClown);

        // Drops the first-level cache so the queries are answered by the
        // database and not by the session.
        em.clear();
    }

    @Test
    @DisplayName("findAllActiveWithCows returns only active clowns")
    void findAllActiveWithCows_returnsActive() {
        List<Clown> result = clownRepository.findAllActiveWithCows();

        // Two clowns in the table, one active: the filter has to leave exactly
        // one standing.
        assertEquals(1, result.size());
        assertEquals("Pennywise", result.getFirst().getName());
    }

    @Test
    @DisplayName("findActiveWithCowsById finds active clown with their cows")
    void findActiveWithCowsById_found() {
        Optional<Clown> result = clownRepository.findActiveWithCowsById(activeClown.getId());

        assertTrue(result.isPresent());
        // The collection arrives populated because the query uses JOIN FETCH
        // across clown_cow. With a LAZY relationship and no fetch, reading this
        // outside the session throws LazyInitializationException.
        assertEquals(1, result.get().getCows().size());
    }

    @Test
    @DisplayName("findActiveByCowId returns clowns that care for a cow")
    void findActiveByCowId_returnsClownsOfCow() {
        // Traverses the N to M in the opposite direction: given a cow, who looks
        // after it. It is the query behind GET /api/clowns/cow/{cowId}, and the
        // one that proves the join table can be read from either end.
        List<Clown> result = clownRepository.findActiveByCowId(cow.getId());

        assertEquals(1, result.size());
        assertEquals("Pennywise", result.getFirst().getName());
    }

    @Test
    @DisplayName("existsByNameIgnoreCase detects duplicate")
    void existsByNameIgnoreCase_works() {
        // Queried in lowercase against a fixture stored as "Pennywise", so the
        // IgnoreCase is what is really under test. Both directions are asserted
        // so a method that always returned true could not pass.
        assertTrue(clownRepository.existsByNameIgnoreCase("pennywise"));
        assertFalse(clownRepository.existsByNameIgnoreCase("NoExiste"));
    }

    @Test
    @DisplayName("existsByNameIgnoreCaseAndIdNot excludes the same clown")
    void existsByNameAndIdNot_excludesSelf() {
        // The variant used on update: the clown must not collide with itself.
        // Without the IdNot, saving a clown without renaming them would be
        // rejected as a duplicate.
        assertFalse(clownRepository.existsByNameIgnoreCaseAndIdNot(
                "Pennywise", activeClown.getId()));
    }
}
