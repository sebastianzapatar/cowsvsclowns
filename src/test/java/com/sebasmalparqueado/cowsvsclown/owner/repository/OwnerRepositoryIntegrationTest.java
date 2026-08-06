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
 * Owner repository integration tests.
 *
 * <p><b>Why these are integration and not unit tests:</b> there is nothing of
 * ours to unit test here. {@code IOwnerRepository} is an interface with no
 * implementation we wrote — Spring Data generates it at runtime from the method
 * names and the {@code @Query} annotations. Mocking it would only assert that
 * Mockito returns what we told it to. The only way to know a query is correct is
 * to run it against a real database.</p>
 *
 * <p>{@code @DataJpaTest} boots <em>only</em> the persistence layer: entities,
 * repositories and the datasource. No controllers, no services, no web server.
 * That is what keeps these tests in the millisecond range while still being
 * real.</p>
 *
 * <p>Each test method runs inside a transaction that is rolled back at the end,
 * so the tests cannot leak state into each other and their order does not
 * matter.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class OwnerRepositoryIntegrationTest {

    @Autowired
    private IOwnerRepository ownerRepository;

    /**
     * Used instead of the repository to build the fixture. The point is to set
     * the scenario up through a different door than the one under test: if a
     * finder is broken, the setup still succeeds and the failure lands on the
     * assertion, where it is readable.
     */
    @Autowired
    private TestEntityManager em;

    private Owner activeOwner;
    private Owner inactiveOwner;

    @BeforeEach
    void setUp() {
        // The fixture is deliberately asymmetric: one active owner (with a cow,
        // to exercise the 1 to N fetch) and one inactive. Every finder below
        // filters by active = true, so without the inactive row the tests would
        // pass even if the WHERE clause were missing.
        activeOwner = Owner.builder()
                .firstName("Sebastián").lastName("Zapata").active(true).build();
        Cow cow = Cow.builder()
                .name("Lola").weight(450).milkperday(12).active(true).build();
        activeOwner.addCow(cow);
        em.persistAndFlush(activeOwner);

        inactiveOwner = Owner.builder()
                .firstName("Juan").lastName("Pérez").active(false).build();
        em.persistAndFlush(inactiveOwner);

        // Empties Hibernate's first-level cache. Without this the entities just
        // persisted stay attached to the session, and a finder could "pass" by
        // returning the cached instance without ever hitting the database —
        // which would hide a broken query.
        em.clear();
    }

    @Test
    @DisplayName("findAllActiveWithCows returns only active owners with their cows")
    void findAllActiveWithCows_returnsOnlyActive() {
        List<Owner> result = ownerRepository.findAllActiveWithCows();

        // Exactly 1: the inactive owner must be filtered out.
        assertEquals(1, result.size());
        assertEquals("Sebastián", result.getFirst().getFirstName());
        // The cows come already loaded. The relationship is LAZY, so this only
        // works because the query uses JOIN FETCH; without it, reading the
        // collection outside the session would blow up with a
        // LazyInitializationException. That is precisely what is being guarded.
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
        // The row exists in the table; what must not appear is a soft-deleted
        // owner. This is the test that would catch someone "simplifying" the
        // query down to a plain findById.
        Optional<Owner> result = ownerRepository.findActiveWithCowsById(inactiveOwner.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByIdAndActiveTrue finds the active owner")
    void findByIdAndActiveTrue_found() {
        // Derived query (Spring Data builds it from the method name), unlike the
        // ones above which are annotated with @Query. It is the cheap lookup for
        // when the cows are not needed.
        Optional<Owner> result = ownerRepository.findByIdAndActiveTrue(activeOwner.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("existsByName detects duplicate (case insensitive)")
    void existsByName_caseInsensitive() {
        // Queried in lowercase against a fixture stored capitalised: this only
        // passes if the IgnoreCase in the method name is doing its job. It backs
        // the business rule that "sebastián zapata" and "Sebastián Zapata" are
        // the same person.
        boolean exists = ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                        "sebastián", "zapata");

        assertTrue(exists);
    }

    @Test
    @DisplayName("existsByName with IdNot excludes the owner being updated")
    void existsByName_excludesSelf() {
        // The variant used on update. Without the IdNot, saving an owner without
        // renaming them would collide with themselves and the service would
        // answer 409 for a perfectly valid edit.
        boolean exists = ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                        "sebastián", "zapata", activeOwner.getId());

        assertFalse(exists);
    }
}
