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
 * Owner service unit tests. Repositories are mocked to
 * isolate the business logic.
 */
@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock
    private IOwnerRepository ownerRepository;

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

        @Test
        @DisplayName("throws 404 if it does not exist")
        void notFound_throws404() {
            when(ownerRepository.findActiveWithCowsById(99L)).thenReturn(Optional.empty());

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
            when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> {
                Owner o = invocation.getArgument(0);
                o.setId(1L);
                return o;
            });

            OwnerResponse result = ownerService.create(request);

            assertNotNull(result);
            assertEquals("Sebastián", result.firstName());
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
            verify(ownerRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws 409 if a cow has a repeated name in the database")
        void duplicateCowName_throws409() {
            OwnerRequest request = new OwnerRequest("Juan", "Pérez",
                    List.of(new OwnerCowRequest("Lola", 450, 12)));

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

        @Test
        @DisplayName("throws 404 if the owner does not exist")
        void notFound_throws404() {
            when(ownerRepository.findActiveWithCowsById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> ownerService.update(99L, new OwnerUpdateRequest("X", null)));
        }

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

            assertFalse(owner.isActive());
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

    private Owner buildOwner(Long id, String firstName, String lastName) {
        return Owner.builder()
                .id(id).firstName(firstName).lastName(lastName).active(true).build();
    }
}
