package com.sebasmalparqueado.cowsvsclown.clowns.service;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.BadRequestException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Clown service unit tests. Repositories are mocked to isolate business logic,
 * especially the N to M relationship.
 *
 * <p>{@code Clown} is the <b>owning side</b> of that relationship: it declares
 * the {@code @JoinTable}, and {@code Cow} mirrors it with {@code mappedBy}. That
 * single fact drives most of what is tested here, because Hibernate only writes
 * {@code clown_cow} rows from the owning side — so linking and unlinking are
 * this service's job, not the cow service's.</p>
 *
 * <p>Hence the dedicated {@code assignCow}/{@code unassignCow} endpoints. A
 * link is not a field of either entity, so it does not belong in a PATCH body;
 * it is a resource of its own that gets created and deleted.</p>
 *
 * <p>The {@code clown_cow} rows are also the one thing in this project that is
 * deleted for real. Everything else is a soft delete, but a join row only
 * represents "this clown looks after this cow" — once that stops being true
 * there is nothing left to keep.</p>
 */
@ExtendWith(MockitoExtension.class)
class ClownServiceTest {

    @Mock
    private IClownRepository clownRepository;

    /** Needed to resolve the cow ids that arrive when creating or assigning. */
    @Mock
    private ICowRepository cowRepository;

    @InjectMocks
    private ClownService clownService;

    // ============================== Lectura ===============================

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns active clowns")
        void returnsActiveClowns() {
            Clown clown = buildClown("Pennywise");
            when(clownRepository.findAllActiveWithCows()).thenReturn(List.of(clown));

            List<ClownResponse> result = clownService.getAll();

            assertEquals(1, result.size());
            assertEquals("Pennywise", result.getFirst().name());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the clown if it exists")
        void returnsClown() {
            Clown clown = buildClown("Pennywise");
            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));

            ClownResponse result = clownService.getById(clown.getId());

            assertEquals("Pennywise", result.name());
        }

        @Test
        @DisplayName("throws 404 if not found")
        void notFound_throws404() {
            UUID id = UUID.randomUUID();
            when(clownRepository.findActiveWithCowsById(id)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> clownService.getById(id));
        }
    }

    /**
     * Traverses the N to M from the cow's end: given a cow, who looks after it.
     * The link is stored once in {@code clown_cow} and can be read from either
     * direction — this is the endpoint that exposes the other one.
     */
    @Nested
    @DisplayName("getByCow")
    class GetByCow {

        @Test
        @DisplayName("returns clowns of a cow")
        void returnsClowns() {
            UUID cowId = UUID.randomUUID();
            Cow cow = buildCow(cowId, "Lola");
            Clown clown = buildClown("Pennywise");

            // The cow is looked up first so that asking about a cow that does not
            // exist is a 404 rather than an empty list. Empty means "nobody looks
            // after this cow", which is a valid answer and a different one.
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));
            when(clownRepository.findActiveByCowId(cowId)).thenReturn(List.of(clown));

            List<ClownResponse> result = clownService.getByCow(cowId);

            assertEquals(1, result.size());
        }
    }

    // ============================== Creación ==============================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates clown with cows")
        void savesWithCows() {
            UUID cowId = UUID.randomUUID();
            ClownRequest request = new ClownRequest("Pennywise", "Terror", List.of(cowId));
            Cow cow = buildCow(cowId, "Lola");

            when(clownRepository.existsByNameIgnoreCase("Pennywise")).thenReturn(false);
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));
            when(clownRepository.save(any(Clown.class))).thenAnswer(invocation -> {
                Clown c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            ClownResponse result = clownService.create(request);

            assertNotNull(result);
            assertEquals("Pennywise", result.name());
        }

        @Test
        @DisplayName("throws 409 if name already exists")
        void duplicateName_throws409() {
            ClownRequest request = new ClownRequest("Pennywise", null, null);
            when(clownRepository.existsByNameIgnoreCase("Pennywise")).thenReturn(true);

            assertThrows(ConflictException.class, () -> clownService.create(request));
            verify(clownRepository, never()).save(any());
        }
    }

    // ============================== Actualización =========================

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("changes given fields")
        void changesFields() {
            Clown clown = buildClown("Pennywise");
            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(clownRepository.existsByNameIgnoreCaseAndIdNot("Bozo", clown.getId()))
                    .thenReturn(false);
            when(clownRepository.save(any(Clown.class))).thenAnswer(i -> i.getArgument(0));

            ClownResponse result = clownService.update(clown.getId(),
                    new ClownUpdateRequest("Bozo", "Nuevo desc"));

            assertEquals("Bozo", result.name());
        }

        @Test
        @DisplayName("throws 400 if request is empty")
        void emptyRequest_throws400() {
            assertThrows(BadRequestException.class,
                    () -> clownService.update(UUID.randomUUID(),
                            new ClownUpdateRequest(null, null)));
        }
    }

    // ============================== N a M =================================

    /**
     * The heart of this service: creating and destroying the link between a
     * clown and a cow.
     *
     * <p>Both operations live here rather than on the cow side because
     * {@code Clown} owns the relationship. They are also separate endpoints
     * rather than fields in a PATCH, and they return 200 rather than 201/204,
     * because what changes is an association and not the clown itself.</p>
     *
     * <p>Every test in this block asserts against the in-memory collection
     * instead of the mock, since the collection is what Hibernate reads when it
     * decides which {@code clown_cow} rows to write or delete.</p>
     */
    @Nested
    @DisplayName("assignCow / unassignCow")
    class NToM {

        @Test
        @DisplayName("assignCow adds relation")
        void assignCow_addsRelation() {
            UUID cowId = UUID.randomUUID();
            Clown clown = buildClown("Pennywise");
            Cow cow = buildCow(cowId, "Lola");

            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));
            when(clownRepository.save(any(Clown.class))).thenAnswer(i -> i.getArgument(0));

            ClownResponse result = clownService.assignCow(clown.getId(), cowId);

            assertNotNull(result);
            // The cow ends up in the clown's collection, which is the owning
            // side: that is what makes Hibernate insert the clown_cow row on
            // flush. Adding it only to cow.getClowns() would change nothing in
            // the database.
            assertTrue(clown.getCows().contains(cow));
        }

        /**
         * Assigning a cow that is already assigned is a 409, not a silent no-op.
         * The join table has no unique constraint to lean on, so without this
         * check a repeated call would either duplicate the row or quietly do
         * nothing while answering 200 — and the caller could not tell which.
         */
        @Test
        @DisplayName("assignCow throws 409 if already assigned")
        void assignCow_alreadyAssigned_throws409() {
            UUID cowId = UUID.randomUUID();
            Clown clown = buildClown("Pennywise");
            Cow cow = buildCow(cowId, "Lola");
            clown.getCows().add(cow);

            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));

            assertThrows(ConflictException.class,
                    () -> clownService.assignCow(clown.getId(), cowId));
        }

        /**
         * The one real delete in the project. Everywhere else a row survives with
         * {@code active = false}, but a {@code clown_cow} row only means "this
         * clown looks after this cow" — once that is no longer true there is
         * nothing worth keeping, and both entities go on existing untouched.
         */
        @Test
        @DisplayName("unassignCow removes relation")
        void unassignCow_removesRelation() {
            UUID cowId = UUID.randomUUID();
            Clown clown = buildClown("Pennywise");
            Cow cow = buildCow(cowId, "Lola");
            // Both sides are wired up here, unlike in assignCow's fixture. The
            // service has to clean up the collection on each side; leaving a
            // stale reference on the cow would resurrect the link if that
            // instance were flushed later in the same session.
            clown.getCows().add(cow);
            cow.getClowns().add(clown);

            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));
            when(clownRepository.save(any(Clown.class))).thenAnswer(i -> i.getArgument(0));

            clownService.unassignCow(clown.getId(), cowId);

            assertFalse(clown.getCows().contains(cow));
        }

        /**
         * The mirror image of the duplicate-assign case: unlinking something
         * that was never linked is a 409. Both entities exist and are active, so
         * it is not a 404 — the request simply contradicts the current state.
         */
        @Test
        @DisplayName("unassignCow throws 409 if not assigned")
        void unassignCow_notAssigned_throws409() {
            UUID cowId = UUID.randomUUID();
            Clown clown = buildClown("Pennywise");
            Cow cow = buildCow(cowId, "Lola");

            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));

            assertThrows(ConflictException.class,
                    () -> clownService.unassignCow(clown.getId(), cowId));
        }
    }

    // ============================== Baja lógica ===========================

    /**
     * Deleting a clown cascades nowhere, unlike deleting an owner. The cows go on
     * existing — they belong to someone else and are merely looked after by this
     * clown — and the {@code clown_cow} rows are left alone: the mappers filter
     * inactive clowns out on the way to the client, so the link stops showing up
     * without having to be torn down.
     */
    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("deactivates clown")
        void deactivatesClown() {
            Clown clown = buildClown("Pennywise");
            // The cheap finder, with no JOIN FETCH: flipping a flag does not
            // need the cows loaded.
            when(clownRepository.findByIdAndActiveTrue(clown.getId()))
                    .thenReturn(Optional.of(clown));

            clownService.softDelete(clown.getId());

            assertFalse(clown.isActive());
            // save() and not delete(): the row survives, only the flag changes.
            verify(clownRepository).save(clown);
        }
    }

    // ============================== Helpers ===============================

    private Clown buildClown(String name) {
        return Clown.builder()
                .id(UUID.randomUUID()).name(name).description("Test")
                .active(true).cows(new ArrayList<>()).build();
    }

    private Cow buildCow(UUID id, String name) {
        Owner owner = Owner.builder()
                .id(1L).firstName("Dueño").lastName("Test").active(true).build();
        return Cow.builder()
                .id(id).name(name).weight(450).milkperday(12)
                .active(true).owner(owner).clowns(new ArrayList<>()).build();
    }
}
