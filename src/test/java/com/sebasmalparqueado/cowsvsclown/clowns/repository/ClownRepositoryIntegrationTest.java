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
 * Clown repository integration tests. Verifies the queries
 * for the N to M relationship with the clown_cow join table.
 */
@DataJpaTest
@ActiveProfiles("test")
class ClownRepositoryIntegrationTest {

    @Autowired
    private IClownRepository clownRepository;

    @Autowired
    private TestEntityManager em;

    private Clown activeClown;
    private Clown inactiveClown;
    private Cow cow;

    @BeforeEach
    void setUp() {
        Owner owner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        em.persistAndFlush(owner);

        cow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).owner(owner).build();
        em.persistAndFlush(cow);

        activeClown = Clown.builder()
                .name("Pennywise").description("Terror").active(true).build();
        activeClown.addCow(cow);
        em.persistAndFlush(activeClown);

        inactiveClown = Clown.builder()
                .name("Bozo").description("Inactivo").active(false).build();
        em.persistAndFlush(inactiveClown);

        em.clear();
    }

    @Test
    @DisplayName("findAllActiveWithCows returns only active clowns")
    void findAllActiveWithCows_returnsActive() {
        List<Clown> result = clownRepository.findAllActiveWithCows();

        assertEquals(1, result.size());
        assertEquals("Pennywise", result.getFirst().getName());
    }

    @Test
    @DisplayName("findActiveWithCowsById finds active clown with their cows")
    void findActiveWithCowsById_found() {
        Optional<Clown> result = clownRepository.findActiveWithCowsById(activeClown.getId());

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getCows().size());
    }

    @Test
    @DisplayName("findActiveByCowId returns clowns that care for a cow")
    void findActiveByCowId_returnsClownsOfCow() {
        List<Clown> result = clownRepository.findActiveByCowId(cow.getId());

        assertEquals(1, result.size());
        assertEquals("Pennywise", result.getFirst().getName());
    }

    @Test
    @DisplayName("existsByNameIgnoreCase detects duplicate")
    void existsByNameIgnoreCase_works() {
        assertTrue(clownRepository.existsByNameIgnoreCase("pennywise"));
        assertFalse(clownRepository.existsByNameIgnoreCase("NoExiste"));
    }

    @Test
    @DisplayName("existsByNameIgnoreCaseAndIdNot excludes the same clown")
    void existsByNameAndIdNot_excludesSelf() {
        assertFalse(clownRepository.existsByNameIgnoreCaseAndIdNot(
                "Pennywise", activeClown.getId()));
    }
}
