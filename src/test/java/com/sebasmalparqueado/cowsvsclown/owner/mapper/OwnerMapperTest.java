package com.sebasmalparqueado.cowsvsclown.owner.mapper;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner mapper unit tests. Verifies that the list of cows
 * of OwnerResponse comes as List&lt;String&gt; (only names), not as objects.
 */
class OwnerMapperTest {

    // ============================== toEntity ==========================================

    @Test
    @DisplayName("toEntity maps name and last name, active = true, without cows")
    void toEntity_mapsBasicFields() {
        OwnerRequest request = new OwnerRequest("Sebastián", "Zapata", null);

        Owner owner = OwnerMapper.toEntity(request);

        assertNotNull(owner);
        assertEquals("Sebastián", owner.getFirstName());
        assertEquals("Zapata", owner.getLastName());
        assertTrue(owner.isActive());
        assertTrue(owner.getCows().isEmpty());
    }

    @Test
    @DisplayName("toEntity with cows adds them to the owner")
    void toEntity_withCows_addsCowsToOwner() {
        List<OwnerCowRequest> cows = List.of(
                new OwnerCowRequest("Lola", 450, 12),
                new OwnerCowRequest("Margarita", 380, 8)
        );
        OwnerRequest request = new OwnerRequest("Sebastián", "Zapata", cows);

        Owner owner = OwnerMapper.toEntity(request);

        assertEquals(2, owner.getCows().size());
        // Each cow has the owner assigned (bidirectional synchronization).
        owner.getCows().forEach(cow -> assertSame(owner, cow.getOwner()));
    }

    @Test
    @DisplayName("toEntity with null returns null")
    void toEntity_withNull_returnsNull() {
        assertNull(OwnerMapper.toEntity(null));
    }

    // ============================== toResponse ========================================

    @Test
    @DisplayName("toResponse returns only the names of active cows")
    void toResponse_returnsCowNamesOnly() {
        Owner owner = Owner.builder()
                .id(1L).firstName("Sebastián").lastName("Zapata").active(true).build();

        Cow activeCow = Cow.builder()
                .id(UUID.randomUUID()).name("Lola").milkperday(12).active(true)
                .owner(owner).build();
        Cow inactiveCow = Cow.builder()
                .id(UUID.randomUUID()).name("Muerta").milkperday(0).active(false)
                .owner(owner).build();

        owner.getCows().addAll(List.of(activeCow, inactiveCow));

        OwnerResponse response = OwnerMapper.toResponse(owner);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Sebastián", response.firstName());
        assertEquals("Zapata", response.lastName());
        assertEquals("Sebastián Zapata", response.fullName());
        assertTrue(response.active());
        // Only the active one
        assertEquals(1, response.totalCows());
        assertEquals(List.of("Lola"), response.cows());
    }

    @Test
    @DisplayName("toResponse filters inactive cows")
    void toResponse_filtersInactiveCows() {
        Owner owner = Owner.builder()
                .id(2L).firstName("Juan").lastName("Pérez").active(true).build();

        Cow inactive1 = Cow.builder()
                .id(UUID.randomUUID()).name("A").active(false).owner(owner).build();
        Cow inactive2 = Cow.builder()
                .id(UUID.randomUUID()).name("B").active(false).owner(owner).build();

        owner.getCows().addAll(List.of(inactive1, inactive2));

        OwnerResponse response = OwnerMapper.toResponse(owner);

        assertEquals(0, response.totalCows());
        assertTrue(response.cows().isEmpty());
    }

    @Test
    @DisplayName("toResponse with null returns null")
    void toResponse_withNull_returnsNull() {
        assertNull(OwnerMapper.toResponse(null));
    }

    // ============================== toSummary =========================================

    @Test
    @DisplayName("toSummary maps id and full name")
    void toSummary_mapsIdAndFullName() {
        Owner owner = Owner.builder()
                .id(1L).firstName("Sebastián").lastName("Zapata").build();

        OwnerSummaryResponse summary = OwnerMapper.toSummary(owner);

        assertNotNull(summary);
        assertEquals(1L, summary.id());
        assertEquals("Sebastián Zapata", summary.fullName());
    }

    @Test
    @DisplayName("toSummary with null returns null")
    void toSummary_withNull_returnsNull() {
        assertNull(OwnerMapper.toSummary(null));
    }
}
