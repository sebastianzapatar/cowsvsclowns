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
 * Owner mapper unit tests.
 *
 * <p>These are the cheapest tests in the suite: {@code OwnerMapper} is a class
 * of static methods with no dependencies, so there is no Spring context and no
 * mocks — just call and assert. They run in microseconds.</p>
 *
 * <p>They are worth having anyway, because the mapper is where two decisions
 * that the API contract depends on actually live:</p>
 *
 * <ul>
 *   <li><b>It filters soft-deleted cows.</b> The entity holds every cow ever
 *       assigned, including the ones marked {@code active = false}. Dropping
 *       them is the mapper's job, and if it stopped doing it the API would start
 *       handing out deleted data without any query changing.</li>
 *   <li><b>{@code OwnerResponse.cows()} is a {@code List<String>}</b>, plain
 *       names rather than objects. That is what keeps the JSON from nesting for
 *       ever: an owner brings the names of its cows, and each cow brings a
 *       summary of its owner, so the cycle is cut on this side.</li>
 * </ul>
 *
 * <p>Every method is also checked against {@code null}. The mapper is called
 * with whatever the repository returns, and a finder that comes back empty would
 * otherwise turn into a NullPointerException in the middle of a response.</p>
 */
class OwnerMapperTest {

    // ============================== toEntity ==========================================
    // Inbound direction: DTO -> entity, used when creating.



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
        // assertSame, not assertEquals: what matters is that every cow points at
        // *this very instance* of the owner, not at an equal one. That is the
        // bidirectional sync, and it is what makes the cascade work — the
        // foreign key owner_id is written from the cow's side, so a cow whose
        // owner reference is left null fails on insert with a NOT NULL
        // violation even though the owner was created fine.
        owner.getCows().forEach(cow -> assertSame(owner, cow.getOwner()));
    }

    @Test
    @DisplayName("toEntity with null returns null")
    void toEntity_withNull_returnsNull() {
        assertNull(OwnerMapper.toEntity(null));
    }

    // ============================== toResponse ========================================
    // Outbound direction: entity -> DTO. This is the one that filters soft-
    // deleted cows and flattens them down to names.

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
        // fullName is not a column: the entity derives it from the two fields.
        assertEquals("Sebastián Zapata", response.fullName());
        assertTrue(response.active());
        // The owner holds two cows, one of them soft-deleted. Only the active
        // one may come out, and totalCows has to agree with the list it
        // accompanies — a count taken before filtering would report 2 here and
        // leave the client with a number that does not match what it received.
        assertEquals(1, response.totalCows());
        // Names, not objects: this is the assertion that pins the shape of the
        // JSON and keeps the owner-cow-owner nesting from recursing.
        assertEquals(List.of("Lola"), response.cows());
    }

    /**
     * The edge case of the previous test: when <em>every</em> cow is inactive
     * the result has to be an empty list, not null. A null here would serialise
     * the field away and force the client to handle two shapes for the same
     * thing.
     */
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
    // The reduced version, used when an owner travels *inside* another response
    // (a cow carrying its owner, for instance). It deliberately does not carry
    // the cow list: that is exactly what would close the cycle and make the JSON
    // recurse for ever.

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
