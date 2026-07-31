package com.sebasmalparqueado.cowsvsclown.clowns.mapper;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clown mapper unit tests. Pure logic, no Spring or mocks.
 */
class ClownMapperTest {

    // ============================== toEntity ==========================================

    @Test
    @DisplayName("toEntity maps name and description, active = true")
    void toEntity_mapsFields() {
        ClownRequest request = new ClownRequest("Pennywise", "Vive en la alcantarilla", null);

        Clown clown = ClownMapper.toEntity(request);

        assertNotNull(clown);
        assertEquals("Pennywise", clown.getName());
        assertEquals("Vive en la alcantarilla", clown.getDescription());
        assertTrue(clown.isActive());
        assertTrue(clown.getCows().isEmpty());
    }

    @Test
    @DisplayName("toEntity with null returns null")
    void toEntity_withNull_returnsNull() {
        assertNull(ClownMapper.toEntity(null));
    }

    // ============================== toResponse ========================================

    @Test
    @DisplayName("toResponse maps all fields with active cows")
    void toResponse_mapsAllFields() {
        UUID clownId = UUID.randomUUID();
        Clown clown = Clown.builder()
                .id(clownId).name("Pennywise").description("Terrorífico").active(true)
                .build();

        Cow activeCow = Cow.builder()
                .id(UUID.randomUUID()).name("Lola").milkperday(12).active(true).build();
        Cow inactiveCow = Cow.builder()
                .id(UUID.randomUUID()).name("Muerta").milkperday(0).active(false).build();

        clown.getCows().addAll(List.of(activeCow, inactiveCow));

        ClownResponse response = ClownMapper.toResponse(clown);

        assertNotNull(response);
        assertEquals(clownId, response.id());
        assertEquals("Pennywise", response.name());
        assertEquals("Terrorífico", response.description());
        assertTrue(response.active());
        // Solo la vaca activa
        assertEquals(1, response.totalCows());
        assertEquals("Lola", response.cows().getFirst().name());
    }

    @Test
    @DisplayName("toResponse filters inactive cows")
    void toResponse_filtersInactiveCows() {
        Clown clown = Clown.builder()
                .id(UUID.randomUUID()).name("Bozo").active(true).build();

        Cow inactive = Cow.builder()
                .id(UUID.randomUUID()).name("X").active(false).build();
        clown.getCows().add(inactive);

        ClownResponse response = ClownMapper.toResponse(clown);

        assertEquals(0, response.totalCows());
        assertTrue(response.cows().isEmpty());
    }

    @Test
    @DisplayName("toResponse with null returns null")
    void toResponse_withNull_returnsNull() {
        assertNull(ClownMapper.toResponse(null));
    }

    // ============================== toSummary =========================================

    @Test
    @DisplayName("toSummary maps id and name")
    void toSummary_mapsIdAndName() {
        UUID id = UUID.randomUUID();
        Clown clown = Clown.builder().id(id).name("Pennywise").build();

        ClownSummaryResponse summary = ClownMapper.toSummary(clown);

        assertNotNull(summary);
        assertEquals(id, summary.id());
        assertEquals("Pennywise", summary.name());
    }

    @Test
    @DisplayName("toSummary with null returns null")
    void toSummary_withNull_returnsNull() {
        assertNull(ClownMapper.toSummary(null));
    }
}
