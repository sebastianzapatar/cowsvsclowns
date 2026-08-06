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
 *
 * <p>This is the busiest of the three mappers, because {@code Cow} sits in the
 * middle of both relationships — it belongs to an owner (1 to N) and is looked
 * after by clowns (N to M). So {@code CowResponse} has to carry both sides
 * without either of them dragging the whole graph along, which it does by
 * reducing each to a summary.</p>
 *
 * <p>It also has <b>two</b> {@code toEntity} overloads, one per way a cow can be
 * born, and a {@code toActiveSummaries} helper used by the other mappers.</p>
 *
 * <p>The rule that runs through all of it: the mapper maps, it does not resolve.
 * Anything that needs a database lookup (turning an {@code ownerId} into an
 * {@code Owner}) belongs to the service — which is why the mapper can stay
 * dependency-free and be tested like this.</p>
 */
class CowMapperTest {

    // ============================== toEntity (CowRequest) ==============================
    // First overload: a cow created on its own via POST /api/cows, where the
    // owner arrives as an id that still has to be looked up.



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
        // Deliberately null: the request carried ownerId = 1, but turning that
        // into an Owner needs the repository. Resolving it here would give the
        // mapper a dependency and cost it these microsecond tests. The service
        // fills it in — and that division of labour is what this assertion pins
        // down, so a future "convenience" lookup inside the mapper fails loudly.
        assertNull(cow.getOwner());
    }

    @Test
    @DisplayName("toEntity(CowRequest) with null returns null")
    void toEntity_fromCowRequest_withNull_returnsNull() {
        // The cast is not decoration: with two toEntity overloads a bare null is
        // ambiguous and would not compile.
        assertNull(CowMapper.toEntity((CowRequest) null));
    }

    // ============================== toEntity (OwnerCowRequest) =========================
    // Second overload: a cow arriving nested inside its owner in
    // POST /api/owners. There is no ownerId here — the owner is the one being
    // created in the same request, and the @OneToMany cascade saves both in a
    // single transaction.

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
    // The outbound direction, and the only place where both relationships meet.
    // The fixture below is built so a single test covers all three concerns:
    // the 1 to N, the N to M, and the soft-delete filter.

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
        // The 1 to N side, reduced to a summary: id and full name, with no cow
        // list of its own. That is what stops cow -> owner -> cows -> owner from
        // recursing for ever in the JSON.
        assertNotNull(response.owner());
        assertEquals(1L, response.owner().id());
        assertEquals("Sebastián Zapata", response.owner().fullName());
        // The N to M side, with the soft-delete filter applied: the cow was
        // built with two clowns and only the active one may come out. Note the
        // asymmetry with the owner — a deleted owner is still reported (the cow
        // has to belong to someone), but a deleted clown simply disappears from
        // the list, because the link is what stopped being true.
        assertEquals(1, response.clowns().size());
        assertEquals("Pennywise", response.clowns().getFirst().name());
    }

    @Test
    @DisplayName("toResponse with null returns null")
    void toResponse_withNull_returnsNull() {
        assertNull(CowMapper.toResponse(null));
    }

    // ============================== toSummary ==========================================
    // The reduced version, used when a cow travels inside a clown's or an
    // owner's response. It carries milk per day because a clown listing is
    // expected to be useful without a second call per cow — but no owner and no
    // clowns, which is what keeps the graph from closing on itself.

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
    // Shared helper: filter a cow collection down to the active ones and reduce
    // each to a summary. It is what ClownMapper leans on, so the soft-delete
    // rule is written once instead of being repeated per mapper.

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

    /**
     * The one method that does <em>not</em> return null for null input, and the
     * asymmetry is deliberate: this one returns a collection. An empty list lets
     * the caller iterate without a null check, and keeps the JSON field as
     * {@code []} instead of making the client handle a missing array.
     */
    @Test
    @DisplayName("toActiveSummaries with null returns empty list")
    void toActiveSummaries_withNull_returnsEmptyList() {
        List<CowSummaryResponse> result = CowMapper.toActiveSummaries(null);
        assertTrue(result.isEmpty());
    }
}
