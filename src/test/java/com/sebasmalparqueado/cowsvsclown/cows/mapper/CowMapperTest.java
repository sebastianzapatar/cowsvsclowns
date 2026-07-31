package com.sebasmalparqueado.cowsvsclown.cows.mapper;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the cow mapper. They are pure tests: they do not boot Spring
 * or mock anything, because the mapper is a stateless utility class.
 */
class CowMapperTest {

    // ============================== toEntity (CowRequest) ==============================

    @Test
    @DisplayName("toEntity(CowRequest) maps name, weight and milk, active = true")
    void toEntity_fromCowRequest_mapsFields() {
        CowRequest request = new CowRequest("Lola", 450, 12, 1L, null);

        Cow cow = CowMapper.toEntity(request);

        assertNotNull(cow);
        assertEquals("Lola", cow.getName());
        assertEquals(450, cow.getWeight());
        assertEquals(12, cow.getMilkperday());
        assertTrue(cow.isActive());
        // The owner is NOT resolved by the mapper, but by the service.
        assertNull(cow.getOwner());
    }

    @Test
    @DisplayName("toEntity(CowRequest) with null returns null")
    void toEntity_fromCowRequest_withNull_returnsNull() {
        assertNull(CowMapper.toEntity((CowRequest) null));
    }

    // ============================== toEntity (OwnerCowRequest) =========================

    @Test
    @DisplayName("toEntity(OwnerCowRequest) maps fields correctly")
    void toEntity_fromOwnerCowRequest_mapsFields() {
        OwnerCowRequest request = new OwnerCowRequest("Margarita", 380, 8);

        Cow cow = CowMapper.toEntity(request);

        assertNotNull(cow);
        assertEquals("Margarita", cow.getName());
        assertEquals(380, cow.getWeight());
        assertEquals(8, cow.getMilkperday());
        assertTrue(cow.isActive());
    }

    @Test
    @DisplayName("toEntity(OwnerCowRequest) with null returns null")
    void toEntity_fromOwnerCowRequest_withNull_returnsNull() {
        assertNull(CowMapper.toEntity((OwnerCowRequest) null));
    }

    // ============================== toResponse =========================================

    @Test
    @DisplayName("toResponse maps all fields, including owner and active clowns")
    void toResponse_mapsAllFields() {
        Owner owner = Owner.builder()
                .id(1L).firstName("Sebastián").lastName("Zapata").active(true).build();

        Clown activeClown = Clown.builder()
                .id(UUID.randomUUID()).name("Pennywise").active(true).build();
        Clown inactiveClown = Clown.builder()
                .id(UUID.randomUUID()).name("Bozo").active(false).build();

        UUID cowId = UUID.randomUUID();
        Cow cow = Cow.builder()
                .id(cowId).name("Lola").weight(450).milkperday(12).active(true)
                .owner(owner)
                .clowns(List.of(activeClown, inactiveClown))
                .build();

        CowResponse response = CowMapper.toResponse(cow);

        assertNotNull(response);
        assertEquals(cowId, response.id());
        assertEquals("Lola", response.name());
        assertEquals(450, response.weight());
        assertEquals(12, response.milkperday());
        assertTrue(response.active());
        // Owner
        assertNotNull(response.owner());
        assertEquals(1L, response.owner().id());
        assertEquals("Sebastián Zapata", response.owner().fullName());
        // Clowns: only the active one
        assertEquals(1, response.clowns().size());
        assertEquals("Pennywise", response.clowns().getFirst().name());
    }

    @Test
    @DisplayName("toResponse with null returns null")
    void toResponse_withNull_returnsNull() {
        assertNull(CowMapper.toResponse(null));
    }

    // ============================== toSummary ==========================================

    @Test
    @DisplayName("toSummary maps id, name and milk")
    void toSummary_mapsIdNameAndMilk() {
        UUID id = UUID.randomUUID();
        Cow cow = Cow.builder().id(id).name("Lola").milkperday(12).build();

        CowSummaryResponse summary = CowMapper.toSummary(cow);

        assertNotNull(summary);
        assertEquals(id, summary.id());
        assertEquals("Lola", summary.name());
        assertEquals(12, summary.milkperday());
    }

    @Test
    @DisplayName("toSummary with null returns null")
    void toSummary_withNull_returnsNull() {
        assertNull(CowMapper.toSummary(null));
    }

    // ============================== toActiveSummaries ===================================

    @Test
    @DisplayName("toActiveSummaries filters inactive cows")
    void toActiveSummaries_filtersInactive() {
        Cow active = Cow.builder()
                .id(UUID.randomUUID()).name("Lola").milkperday(12).active(true).build();
        Cow inactive = Cow.builder()
                .id(UUID.randomUUID()).name("Muerta").milkperday(0).active(false).build();

        List<CowSummaryResponse> result = CowMapper.toActiveSummaries(List.of(active, inactive));

        assertEquals(1, result.size());
        assertEquals("Lola", result.getFirst().name());
    }

    @Test
    @DisplayName("toActiveSummaries with null returns empty list")
    void toActiveSummaries_withNull_returnsEmptyList() {
        List<CowSummaryResponse> result = CowMapper.toActiveSummaries(null);
        assertTrue(result.isEmpty());
    }
}
