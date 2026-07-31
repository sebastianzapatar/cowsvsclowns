package com.sebasmalparqueado.cowsvsclown.owner.repository;

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
 * Owner repository integration tests. Uses in-memory H2 with
 * {@code @DataJpaTest} which only boots the persistence layer.
 */
@DataJpaTest
@ActiveProfiles("test")
class OwnerRepositoryIntegrationTest {

    @Autowired
    private IOwnerRepository ownerRepository;

    @Autowired
    private TestEntityManager em;

    private Owner activeOwner;
    private Owner inactiveOwner;

    @BeforeEach
    void setUp() {
        activeOwner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        Cow cow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).build();
        activeOwner.addCow(cow);
        em.persistAndFlush(activeOwner);

        inactiveOwner = Owner.builder()
                .firstName("Juan").lastName("Pérez").active(false).build();
        em.persistAndFlush(inactiveOwner);

        em.clear(); // Clear first-level cache
    }

    @Test
    @DisplayName("findAllActiveWithCows returns only active owners with their cows")
    void findAllActiveWithCows_returnsOnlyActive() {
        List<Owner> result = ownerRepository.findAllActiveWithCows();

        assertEquals(1, result.size());
        assertEquals("Sebastián", result.getFirst().getFirstName());
        assertFalse(result.getFirst().getCows().isEmpty());
    }

    @Test
    @DisplayName("findActiveWithCowsById finds the active owner")
    void findActiveWithCowsById_found() {
        Optional<Owner> result = ownerRepository.findActiveWithCowsById(activeOwner.getId());

        assertTrue(result.isPresent());
        assertEquals("Sebastián", result.get().getFirstName());
        assertEquals(1, result.get().getCows().size());
    }

    @Test
    @DisplayName("findActiveWithCowsById does not find the inactive owner")
    void findActiveWithCowsById_inactive_notFound() {
        Optional<Owner> result = ownerRepository.findActiveWithCowsById(inactiveOwner.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByIdAndActiveTrue finds the active owner")
    void findByIdAndActiveTrue_found() {
        Optional<Owner> result = ownerRepository.findByIdAndActiveTrue(activeOwner.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("existsByName detects duplicate (case insensitive)")
    void existsByName_caseInsensitive() {
        boolean exists = ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                        "sebastián", "zapata");

        assertTrue(exists);
    }

    @Test
    @DisplayName("existsByName with IdNot excludes the owner being updated")
    void existsByName_excludesSelf() {
        boolean exists = ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                        "sebastián", "zapata", activeOwner.getId());

        assertFalse(exists);
    }
}
