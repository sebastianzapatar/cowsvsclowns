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
 * Clown mapper unit tests. Pure logic: no Spring context, no mocks, no database
 * — {@code ClownMapper} is static methods over plain objects.
 *
 * <p>The difference from the other two mappers is which side of the N to M this
 * one is on. A {@code ClownResponse} carries its cows as
 * {@code CowSummaryResponse} objects (id, name, milk per day) rather than the
 * bare names an {@code OwnerResponse} uses, because a clown listing is expected
 * to be useful on its own without a second round trip per cow.</p>
 *
 * <p>The summary is also what breaks the cycle: cow summaries do not carry their
 * own clowns back, so clown → cow → clown cannot recurse.</p>
 *
 * <p>As with the other mappers, the soft-delete filter lives here, and every
 * method is checked against {@code null} input.</p>
 */
class ClownMapperTest {

    // ============================== toEntity ==========================================
    // Inbound direction: DTO -> entity, used when creating.



    @Test
    @DisplayName("toEntity maps name and description, active = true")
    void toEntity_mapsFields() {
        // cowIds arrives null: the mapper does not resolve the N to M. Turning
        // an id into a Cow needs the repository, and the mapper has no
        // dependencies on purpose. The service does that part.
        ClownRequest request = new ClownRequest("Pennywise", "Vive en la alcantarilla", null);

        Clown clown = ClownMapper.toEntity(request);

        assertNotNull(clown);
        assertEquals("Pennywise", clown.getName());
        assertEquals("Vive en la alcantarilla", clown.getDescription());
        // active is not in the DTO: a clown is born active, and the client
        // cannot create one already deleted.
        assertTrue(clown.isActive());
        // Empty rather than null, so the service can add cows to it without a
        // null check first.
        assertTrue(clown.getCows().isEmpty());
    }

    @Test
    @DisplayName("toEntity with null returns null")
    void toEntity_withNull_returnsNull() {
        assertNull(ClownMapper.toEntity(null));
    }

    // ============================== toResponse ========================================
    // Outbound direction: entity -> DTO. Filters soft-deleted cows and reduces
    // each surviving one to a summary.

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
        // The clown looks after two cows, one soft-deleted. Only the active one
        // may surface, and totalCows has to agree with the list it accompanies.
        assertEquals(1, response.totalCows());
        // Objects here, not plain names as in OwnerResponse: a cow summary
        // carries id, name and milk per day, but no clowns of its own — which is
        // what keeps clown -> cow -> clown from recursing.
        assertEquals("Lola", response.cows().getFirst().name());
    }

    /**
     * Edge case of the previous test: with every cow inactive the list must come
     * back empty, never null, so the client always sees the same shape.
     */
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
    // The reduced version, used when a clown travels inside a cow's response.
    // It carries no cows, which is the other half of what stops the cycle.

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
