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
 * Clown service unit tests. Repositories are mocked to
 * isolate business logic, especially the N to M relationship.
 */
@ExtendWith(MockitoExtension.class)
class ClownServiceTest {

    @Mock
    private IClownRepository clownRepository;

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

    @Nested
    @DisplayName("getByCow")
    class GetByCow {

        @Test
        @DisplayName("returns clowns of a cow")
        void returnsClowns() {
            UUID cowId = UUID.randomUUID();
            Cow cow = buildCow(cowId, "Lola");
            Clown clown = buildClown("Pennywise");

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
            assertTrue(clown.getCows().contains(cow));
        }

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

        @Test
        @DisplayName("unassignCow removes relation")
        void unassignCow_removesRelation() {
            UUID cowId = UUID.randomUUID();
            Clown clown = buildClown("Pennywise");
            Cow cow = buildCow(cowId, "Lola");
            clown.getCows().add(cow);
            cow.getClowns().add(clown);

            when(clownRepository.findActiveWithCowsById(clown.getId()))
                    .thenReturn(Optional.of(clown));
            when(cowRepository.findByIdAndActiveTrue(cowId)).thenReturn(Optional.of(cow));
            when(clownRepository.save(any(Clown.class))).thenAnswer(i -> i.getArgument(0));

            clownService.unassignCow(clown.getId(), cowId);

            assertFalse(clown.getCows().contains(cow));
        }

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

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("deactivates clown")
        void deactivatesClown() {
            Clown clown = buildClown("Pennywise");
            when(clownRepository.findByIdAndActiveTrue(clown.getId()))
                    .thenReturn(Optional.of(clown));

            clownService.softDelete(clown.getId());

            assertFalse(clown.isActive());
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
