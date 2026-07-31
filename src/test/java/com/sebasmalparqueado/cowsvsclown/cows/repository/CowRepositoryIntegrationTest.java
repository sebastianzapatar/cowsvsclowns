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
 * Cow repository integration tests. Verifies JPQL and native queries
 * against H2 in PostgreSQL mode.
 */
@DataJpaTest
@ActiveProfiles("test")
class CowRepositoryIntegrationTest {

    @Autowired
    private ICowRepository cowRepository;

    @Autowired
    private TestEntityManager em;

    private Owner owner;
    private Cow activeCow;
    private Cow inactiveCow;

    @BeforeEach
    void setUp() {
        owner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        em.persistAndFlush(owner);

        activeCow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).owner(owner).build();
        em.persistAndFlush(activeCow);

        inactiveCow = Cow.builder()
                .name("Muerta").weight(300).milkperday(0).active(false).owner(owner).build();
        em.persistAndFlush(inactiveCow);

        em.clear();
    }

    @Test
    @DisplayName("findAllActiveWithRelations returns only active cows with owner")
    void findAllActiveWithRelations_returnsActive() {
        List<Cow> result = cowRepository.findAllActiveWithRelations();

        assertEquals(1, result.size());
        assertEquals("Lola", result.getFirst().getName());
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
        Optional<Cow> result = cowRepository.findByNameSQL("lola");

        assertTrue(result.isPresent());
        assertEquals("Lola", result.get().getName());
    }

    @Test
    @DisplayName("findByNameSQL does not find inactive cows")
    void findByNameSQL_inactiveNotFound() {
        Optional<Cow> result = cowRepository.findByNameSQL("Muerta");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("existsByNameIgnoreCase detects duplicate")
    void existsByNameIgnoreCase_works() {
        assertTrue(cowRepository.existsByNameIgnoreCase("lola"));
        assertFalse(cowRepository.existsByNameIgnoreCase("NoExiste"));
    }

    @Test
    @DisplayName("findAllByOwnerIdAndActiveTrueOrderByNameAsc returns owner's cows")
    void findByOwnerId_returnsOnlyActive() {
        List<Cow> result = cowRepository
                .findAllByOwnerIdAndActiveTrueOrderByNameAsc(owner.getId());

        assertEquals(1, result.size());
        assertEquals("Lola", result.getFirst().getName());
    }
}
