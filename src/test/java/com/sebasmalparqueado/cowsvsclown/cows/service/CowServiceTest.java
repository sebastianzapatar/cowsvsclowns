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
 * Cow service unit tests. Repositories and the
 * OwnerService are mocked to isolate the logic.
 */
@ExtendWith(MockitoExtension.class)
class CowServiceTest {

    @Mock
    private ICowRepository cowRepository;

    @Mock
    private IClownRepository clownRepository;

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
            assertSame(newOwner, cow.getOwner());
        }

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

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("deactivates the cow")
        void deactivatesCow() {
            Cow cow = buildCow("Lola");
            when(cowRepository.findByIdAndActiveTrue(cow.getId()))
                    .thenReturn(Optional.of(cow));

            cowService.softDelete(cow.getId());

            assertFalse(cow.isActive());
            verify(cowRepository).save(cow);
        }
    }

    // ============================== Helpers ===============================

    private Cow buildCow(String name) {
        Owner owner = buildOwner(1L);
        Cow cow = Cow.builder()
                .id(UUID.randomUUID()).name(name).weight(450).milkperday(12)
                .active(true).owner(owner).build();
        return cow;
    }

    private Owner buildOwner(Long id) {
        return Owner.builder()
                .id(id).firstName("Dueño").lastName("Test").active(true).build();
    }
}
