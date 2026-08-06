package com.sebasmalparqueado.cowsvsclown.owner.service;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import com.sebasmalparqueado.cowsvsclown.owner.repository.IOwnerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Owner service unit tests. Repositories are mocked to isolate the business
 * logic.
 *
 * <p>{@code MockitoExtension} instead of {@code @SpringBootTest}: no context is
 * booted, so these run in microseconds. The trade-off is explicit — the wiring
 * is <em>not</em> under test here. Whether the queries are correct is the
 * repository tests' job, and whether the layers fit together is the e2e tests'.
 * What is tested here is the decision-making: which check runs before which, and
 * what is thrown when one fails.</p>
 *
 * <p>The pattern that repeats throughout: assert the exception <b>and</b> verify
 * that nothing was saved. Only asserting the throw would still pass if the
 * service had written to the database first and validated afterwards — the
 * order matters, and {@code verify(..., never())} is what pins it down.</p>
 *
 * <p>Two repositories are mocked because creating an owner can create cows in
 * the same transaction, and their names have to be checked for duplicates too.</p>
 */
@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock
    private IOwnerRepository ownerRepository;

    /**
     * Needed even though this is the owner service: {@code POST /api/owners}
     * accepts nested cows, so their names are validated against this repository
     * before the cascade runs.
     */
    @Mock
    private ICowRepository cowRepository;

    @InjectMocks
    private OwnerService ownerService;

    // ============================== Read ===============================

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns active owners")
        void returnsActiveOwners() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findAllActiveWithCows()).thenReturn(List.of(owner));

            List<OwnerResponse> result = ownerService.getAll();

            assertEquals(1, result.size());
            assertEquals("Sebastián", result.getFirst().firstName());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the owner if it exists and is active")
        void returnsOwner() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findActiveWithCowsById(1L)).thenReturn(Optional.of(owner));

            OwnerResponse result = ownerService.getById(1L);

            assertEquals("Sebastián", result.firstName());
        }

        /**
         * The repository returns an empty {@code Optional} both when the row does
         * not exist and when it is soft-deleted — the query filters by
         * {@code active = true}. From the caller's side the two are the same
         * thing, and that is on purpose: a deleted owner must look absent, not
         * hidden, or the API would leak which ids once existed.
         */
        @Test
        @DisplayName("throws 404 if it does not exist")
        void notFound_throws404() {
            when(ownerRepository.findActiveWithCowsById(99L)).thenReturn(Optional.empty());

            // The service turns the empty Optional into an exception rather than
            // returning null, so the controller never has to null-check and the
            // 404 is decided in one place.
            assertThrows(ResourceNotFoundException.class, () -> ownerService.getById(99L));
        }
    }

    // ============================== Creation ==============================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates the owner with their cows")
        void savesOwnerWithCows() {
            OwnerRequest request = new OwnerRequest("Sebastián", "Zapata",
                    List.of(new OwnerCowRequest("Lola", 450, 12)));

            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                    anyString(), anyString())).thenReturn(false);
            when(cowRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
            // thenAnswer, not thenReturn: the id is assigned by the database on
            // insert, and the response has to carry it. Simulating that here is
            // what lets the assertions below see a fully-formed owner.
            when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> {
                Owner o = invocation.getArgument(0);
                o.setId(1L);
                return o;
            });

            OwnerResponse result = ownerService.create(request);

            assertNotNull(result);
            assertEquals("Sebastián", result.firstName());
            // The cow travelled with the owner in a single call: this is the
            // 1 to N cascade, and totalCows = 1 is the evidence it ran.
            assertEquals(1, result.totalCows());
            verify(ownerRepository).save(any(Owner.class));
        }

        @Test
        @DisplayName("throws 409 if the owner already exists")
        void duplicateName_throws409() {
            OwnerRequest request = new OwnerRequest("Sebastián", "Zapata", null);
            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                    "Sebastián", "Zapata")).thenReturn(true);

            assertThrows(ConflictException.class, () -> ownerService.create(request));
            // The important half of this test. Without it the service could
            // save first and check afterwards, leaving the duplicate row behind
            // even though the client correctly received a 409.
            verify(ownerRepository, never()).save(any());
        }

        /**
         * The nested cows are validated too, before anything is written. Without
         * this the cascade would try to insert a duplicate cow name and the
         * database would answer with a constraint violation — which reaches the
         * client as a generic 409 with no indication of <em>which</em> cow was
         * the problem.
         */
        @Test
        @DisplayName("throws 409 if a cow has a repeated name in the database")
        void duplicateCowName_throws409() {
            OwnerRequest request = new OwnerRequest("Juan", "Pérez",
                    List.of(new OwnerCowRequest("Lola", 450, 12)));

            // The owner's own name is fine: the only thing that fails is the cow,
            // which is what isolates this test from the one above.
            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                    anyString(), anyString())).thenReturn(false);
            when(cowRepository.existsByNameIgnoreCase("Lola")).thenReturn(true);

            assertThrows(ConflictException.class, () -> ownerService.create(request));
        }
    }

    // ============================== Update =========================

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("changes the first and last name")
        void changesName() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findActiveWithCowsById(1L)).thenReturn(Optional.of(owner));
            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                    anyString(), anyString(), anyLong())).thenReturn(false);
            when(ownerRepository.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));

            OwnerUpdateRequest request = new OwnerUpdateRequest("Juan", "Pérez");
            OwnerResponse result = ownerService.update(1L, request);

            assertEquals("Juan", result.firstName());
            assertEquals("Pérez", result.lastName());
        }

        /**
         * PATCH semantics: a null field means "leave it alone", not "set it to
         * null". Only the first name is sent, so the last name has to survive —
         * an implementation that copied the DTO across wholesale would blank it
         * out, and only this test would notice.
         */
        @Test
        @DisplayName("a null field is left untouched (PATCH, not PUT)")
        void nullField_isNotOverwritten() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findActiveWithCowsById(1L)).thenReturn(Optional.of(owner));
            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                    anyString(), anyString(), anyLong())).thenReturn(false);
            when(ownerRepository.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));

            OwnerResponse result = ownerService.update(1L, new OwnerUpdateRequest("Juan", null));

            assertEquals("Juan", result.firstName());
            assertEquals("Zapata", result.lastName());
        }

        @Test
        @DisplayName("throws 404 if the owner does not exist")
        void notFound_throws404() {
            when(ownerRepository.findActiveWithCowsById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> ownerService.update(99L, new OwnerUpdateRequest("X", null)));
        }

        /**
         * Note the arguments the mock is primed with: {@code "Juan", "Zapata"}.
         * Only the first name was sent, so the duplicate check has to run against
         * the <em>resulting</em> full name — the new first name combined with the
         * last name already stored. Validating against the DTO alone would
         * compare "Juan" with a null last name and miss the collision entirely.
         *
         * <p>The {@code IdNot} variant is what keeps an owner from colliding with
         * itself when the name is not actually changing.</p>
         */
        @Test
        @DisplayName("throws 409 if the new name already exists")
        void duplicateName_throws409() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findActiveWithCowsById(1L)).thenReturn(Optional.of(owner));
            when(ownerRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                    "Juan", "Zapata", 1L)).thenReturn(true);

            assertThrows(ConflictException.class,
                    () -> ownerService.update(1L, new OwnerUpdateRequest("Juan", null)));
        }
    }

    // ============================== Logical delete ===========================

    /**
     * Nothing is ever deleted for real: the row stays and {@code active} flips to
     * false. The interesting part is that the deletion <b>cascades</b> to the
     * cows, because a cow cannot exist without an owner (its {@code owner_id} is
     * NOT NULL). Deactivating only the owner would leave cows pointing at someone
     * who no longer appears anywhere.
     */
    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("deactivates the owner and their cows")
        void deactivatesOwnerAndCows() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            Cow cow = Cow.builder()
                    .id(UUID.randomUUID()).name("Lola").active(true).owner(owner).build();
            owner.getCows().add(cow);

            when(ownerRepository.findActiveWithCowsById(1L)).thenReturn(Optional.of(owner));
            when(ownerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ownerService.softDelete(1L);

            // Asserted on the fixture instance, not on a return value:
            // softDelete returns void, so what is checked is that the service
            // mutated the entity it was handed before saving it.
            assertFalse(owner.isActive());
            // The cascade. This is the assertion that matters — without it the
            // cow would stay active and keep showing up in listings while its
            // owner is gone.
            assertFalse(cow.isActive());
            verify(ownerRepository).save(owner);
        }

        @Test
        @DisplayName("throws 404 if the owner does not exist")
        void notFound_throws404() {
            when(ownerRepository.findActiveWithCowsById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> ownerService.softDelete(99L));
        }
    }

    // ============================== Internal use ===========================

    /**
     * Not part of the REST surface: it is what {@code CowService} calls to
     * resolve an {@code ownerId} into a real owner when creating or reassigning a
     * cow. It returns the <b>entity</b> rather than a DTO precisely because the
     * caller needs something it can attach to a relationship.
     *
     * <p>The {@code OrThrow} in the name is the contract: the caller never has to
     * handle an absent owner, so the 404 for "you referenced an owner that does
     * not exist" is decided here instead of being repeated in every service that
     * points at one.</p>
     */
    @Nested
    @DisplayName("getActiveEntityOrThrow")
    class GetActiveEntity {

        @Test
        @DisplayName("returns the entity if it exists")
        void returnsEntity() {
            Owner owner = buildOwner(1L, "Sebastián", "Zapata");
            when(ownerRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(owner));

            Owner result = ownerService.getActiveEntityOrThrow(1L);

            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("throws 404 if it does not exist")
        void notFound_throws404() {
            when(ownerRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> ownerService.getActiveEntityOrThrow(99L));
        }
    }

    // ============================== Helpers ===============================

    /**
     * Builds an active owner with no cows. Kept as a helper so each test only
     * spells out the part it actually cares about: a test about duplicate names
     * says nothing about cows, and the reader can tell at a glance that they are
     * irrelevant to it.
     */
    private Owner buildOwner(Long id, String firstName, String lastName) {
        return Owner.builder()
                .id(id).firstName(firstName).lastName(lastName).active(true).build();
    }
}
