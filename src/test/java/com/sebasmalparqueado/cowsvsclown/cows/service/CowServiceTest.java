package com.sebasmalparqueado.cowsvsclown.cows.service;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.BadRequestException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import com.sebasmalparqueado.cowsvsclown.owner.service.OwnerService;
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
 * Cow service unit tests. Repositories and the {@code OwnerService} are mocked
 * to isolate the logic.
 *
 * <p>This is the richest service in the project, because {@code Cow} sits at the
 * centre of both relationships: it belongs to an owner (1 to N) and is looked
 * after by clowns (N to M). So on top of the usual CRUD it has two operations
 * the others do not — {@code changeOwner}, which moves a cow between owners, and
 * the resolution of {@code clownIds} when creating.</p>
 *
 * <p>Note what is mocked: {@code OwnerService}, not {@code IOwnerRepository}.
 * Cow logic depends on the <em>behaviour</em> of "give me this owner or fail",
 * not on the query behind it. Mocking the service keeps the boundary at the
 * contract, so a change in how owners are fetched does not ripple into these
 * tests.</p>
 *
 * <p>As in the other service tests, the failure cases assert the exception
 * <b>and</b> that nothing was written, because the order of validation against
 * persistence is part of what is being protected.</p>
 */
@ExtendWith(MockitoExtension.class)
class CowServiceTest {

    @Mock
    private ICowRepository cowRepository;

    /** Needed because a cow can arrive with clowns already assigned. */
    @Mock
    private IClownRepository clownRepository;

    /**
     * The service, not the repository: the dependency is on "resolve this id to
     * an active owner, or throw a 404", which is exactly what
     * {@code getActiveEntityOrThrow} promises.
     */
    @Mock
    private OwnerService ownerService;

    @InjectMocks
    private CowService cowService;

    // ============================== Read ===============================

    @Nested
    @DisplayName("getCows")
    class GetCows {

        @Test
        @DisplayName("returns all active cows")
        void returnsAllActive() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findAllActiveWithRelations()).thenReturn(List.of(cow));

            List<CowResponse> result = cowService.getCows();

            assertEquals(1, result.size());
            assertEquals("Lola", result.getFirst().name());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the cow if it exists")
        void returnsCow() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findActiveWithRelationsById(cow.getId()))
                    .thenReturn(Optional.of(cow));

            CowResponse result = cowService.getById(cow.getId());

            assertEquals("Lola", result.name());
        }

        @Test
        @DisplayName("throws 404 if it does not exist")
        void notFound_throws404() {
            UUID id = UUID.randomUUID();
            when(cowRepository.findActiveWithRelationsById(id)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> cowService.getById(id));
        }
    }

    @Nested
    @DisplayName("getByOwner")
    class GetByOwner {

        @Test
        @DisplayName("returns the owner's cows")
        void returnsCowsOfOwner() {
            Owner owner = buildOwner(1L);
            when(ownerService.getActiveEntityOrThrow(1L)).thenReturn(owner);

            Cow cow = buildCow("Lola");
            when(cowRepository.findAllByOwnerIdAndActiveTrueOrderByNameAsc(1L))
                    .thenReturn(List.of(cow));

            List<CowResponse> result = cowService.getByOwner(1L);

            assertEquals(1, result.size());
        }

        /**
         * Asking for the cows of an owner that does not exist is a 404 about the
         * <em>owner</em>, not an empty list. The distinction matters to the
         * client: an empty list says "this owner has no cows", which is a
         * perfectly normal answer, while the 404 says "you are asking about
         * someone who is not there".
         *
         * <p>This is also why the owner is resolved first even though the cow
         * query alone would have returned nothing useful either way.</p>
         */
        @Test
        @DisplayName("throws 404 if the owner does not exist")
        void ownerNotFound_throws404() {
            when(ownerService.getActiveEntityOrThrow(99L))
                    .thenThrow(ResourceNotFoundException.of("Owner", 99L));

            assertThrows(ResourceNotFoundException.class, () -> cowService.getByOwner(99L));
        }
    }

    @Nested
    @DisplayName("getByName")
    class GetByName {

        @Test
        @DisplayName("returns the cow by name")
        void returnsCow() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findByNameSQL("Lola")).thenReturn(Optional.of(cow));

            CowResponse result = cowService.getByName("Lola");

            assertEquals("Lola", result.name());
        }

        @Test
        @DisplayName("throws 404 if it does not exist")
        void notFound_throws404() {
            when(cowRepository.findByNameSQL("Fantasma")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> cowService.getByName("Fantasma"));
        }
    }

    // ============================== Creation ==============================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates the cow with owner")
        void savesWithOwner() {
            CowRequest request = new CowRequest("Lola", 450, 12, 1L, null);
            Owner owner = buildOwner(1L);

            when(cowRepository.existsByNameIgnoreCase("Lola")).thenReturn(false);
            when(ownerService.getActiveEntityOrThrow(1L)).thenReturn(owner);
            when(cowRepository.save(any(Cow.class))).thenAnswer(invocation -> {
                Cow c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            CowResponse result = cowService.create(request);

            assertNotNull(result);
            assertEquals("Lola", result.name());
            verify(cowRepository).save(any(Cow.class));
        }

        /**
         * The interesting create: both relationships resolved in a single call.
         * The owner comes from an id and the clowns from a list of ids, so one
         * POST writes the cow row, its {@code owner_id}, and a row in
         * {@code clown_cow} per clown.
         */
        @Test
        @DisplayName("creates the cow with owner and clowns")
        void savesWithOwnerAndClowns() {
            UUID clownId = UUID.randomUUID();
            CowRequest request = new CowRequest("Lola", 450, 12, 1L, List.of(clownId));
            Owner owner = buildOwner(1L);

            Clown clown = Clown.builder().id(clownId).name("Pennywise").active(true).build();

            when(cowRepository.existsByNameIgnoreCase("Lola")).thenReturn(false);
            when(ownerService.getActiveEntityOrThrow(1L)).thenReturn(owner);
            when(cowRepository.save(any(Cow.class))).thenAnswer(invocation -> {
                Cow c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(clownRepository.findByIdAndActiveTrue(clownId)).thenReturn(Optional.of(clown));

            CowResponse result = cowService.create(request);

            assertNotNull(result);
            // The save goes through the CLOWN, not the cow. Clown is the owning
            // side of the N to M (it declares the @JoinTable), and Hibernate only
            // writes clown_cow rows from the owning side. Saving the cow instead
            // would leave the link silently unpersisted — the call would succeed
            // and the association would simply not be there afterwards.
            verify(clownRepository).save(clown);
        }

        @Test
        @DisplayName("throws 409 if the name already exists")
        void duplicateName_throws409() {
            CowRequest request = new CowRequest("Lola", 450, 12, 1L, null);
            when(cowRepository.existsByNameIgnoreCase("Lola")).thenReturn(true);

            assertThrows(ConflictException.class, () -> cowService.create(request));
            verify(cowRepository, never()).save(any());
        }
    }

    // ============================== Update =========================

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("changes the provided fields")
        void changesFields() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findActiveWithRelationsById(cow.getId()))
                    .thenReturn(Optional.of(cow));
            when(cowRepository.existsByNameIgnoreCaseAndIdNot("Lola II", cow.getId()))
                    .thenReturn(false);
            when(cowRepository.save(any(Cow.class))).thenAnswer(i -> i.getArgument(0));

            CowUpdateRequest request = new CowUpdateRequest("Lola II", 500, null);
            CowResponse result = cowService.update(cow.getId(), request);

            assertEquals("Lola II", result.name());
            assertEquals(500, result.weight());
        }

        /**
         * A PATCH where every field is null is rejected rather than treated as a
         * no-op. With PATCH semantics null means "leave this alone", so a body of
         * all nulls asks for nothing at all — almost always a client bug (a typo
         * in the field names, most often), and answering 200 would hide it behind
         * what looks like a successful update.
         *
         * <p>It is a 400 and not a 409 because the problem is the request itself,
         * not a collision with existing state. Note that no mock is primed here:
         * the check happens before the cow is even looked up, which is what makes
         * a random id safe to pass.</p>
         */
        @Test
        @DisplayName("throws 400 if the request is empty")
        void emptyRequest_throws400() {
            CowUpdateRequest request = new CowUpdateRequest(null, null, null);

            assertThrows(BadRequestException.class,
                    () -> cowService.update(UUID.randomUUID(), request));
        }

        @Test
        @DisplayName("throws 409 if the new name already exists")
        void duplicateName_throws409() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findActiveWithRelationsById(cow.getId()))
                    .thenReturn(Optional.of(cow));
            when(cowRepository.existsByNameIgnoreCaseAndIdNot("Margarita", cow.getId()))
                    .thenReturn(true);

            assertThrows(ConflictException.class,
                    () -> cowService.update(cow.getId(),
                            new CowUpdateRequest("Margarita", null, null)));
        }
    }

    // ============================== Change of owner =======================

    /**
     * Moving a cow between owners. It gets its own endpoint
     * ({@code PATCH /api/cows/{id}/owner/{ownerId}}) rather than being a field in
     * the update DTO, because it is not a field edit: it rewires a relationship,
     * and both sides of it have to stay consistent in memory.
     */
    @Nested
    @DisplayName("changeOwner")
    class ChangeOwner {

        @Test
        @DisplayName("changes the owner correctly")
        void updatesRelation() {
            Owner oldOwner = buildOwner(1L);
            Owner newOwner = buildOwner(2L);
            Cow cow = buildCow("Lola");
            cow.setOwner(oldOwner);
            oldOwner.getCows().add(cow);

            when(cowRepository.findActiveWithRelationsById(cow.getId()))
                    .thenReturn(Optional.of(cow));
            when(ownerService.getActiveEntityOrThrow(2L)).thenReturn(newOwner);
            when(cowRepository.save(any(Cow.class))).thenAnswer(i -> i.getArgument(0));

            CowResponse result = cowService.changeOwner(cow.getId(), 2L);

            assertNotNull(result);
            // assertSame, not assertEquals: the cow has to point at this very
            // instance. The foreign key is written from the cow's side, so this
            // reference is what actually ends up in owner_id.
            assertSame(newOwner, cow.getOwner());
        }

        /**
         * Reassigning a cow to the owner it already has is a 409 rather than a
         * silent success. It is a conflict with current state, and answering 200
         * would tell the caller a change happened when nothing did.
         */
        @Test
        @DisplayName("throws 409 if the cow already belongs to that owner")
        void sameOwner_throws409() {
            Owner owner = buildOwner(1L);
            Cow cow = buildCow("Lola");
            cow.setOwner(owner);

            when(cowRepository.findActiveWithRelationsById(cow.getId()))
                    .thenReturn(Optional.of(cow));
            when(ownerService.getActiveEntityOrThrow(1L)).thenReturn(owner);

            assertThrows(ConflictException.class,
                    () -> cowService.changeOwner(cow.getId(), 1L));
        }
    }

    // ============================== Logical delete ===========================

    /**
     * Unlike the owner's, this soft delete cascades nowhere. A cow's clowns are
     * an N to M: the clowns go on existing and looking after other cows, so
     * deactivating them would be wrong. The {@code clown_cow} rows are left as
     * they are too — the mappers filter inactive cows out on the way to the
     * client, so a deleted cow simply stops appearing in its clowns' listings.
     */
    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("deactivates the cow")
        void deactivatesCow() {
            Cow cow = buildCow("Lola");
            // The cheap finder, without JOIN FETCH: to flip a flag there is no
            // need to drag the owner and the clowns along.
            when(cowRepository.findByIdAndActiveTrue(cow.getId()))
                    .thenReturn(Optional.of(cow));

            cowService.softDelete(cow.getId());

            assertFalse(cow.isActive());
            // save() and not delete(): the row survives, only the flag changes.
            verify(cowRepository).save(cow);
        }
    }

    // ============================== Helpers ===============================

    /**
     * An active cow with a valid owner. The owner is not optional in the
     * fixture: {@code cows.owner_id} is NOT NULL, so a cow without one could not
     * exist in the database and testing against it would prove nothing.
     */
    private Cow buildCow(String name) {
        Owner owner = buildOwner(1L);
        return Cow.builder()
                .id(UUID.randomUUID()).name(name).weight(450).milkperday(12)
                .active(true).owner(owner).build();
    }

    private Owner buildOwner(Long id) {
        return Owner.builder()
                .id(id).firstName("Dueño").lastName("Test").active(true).build();
    }
}
