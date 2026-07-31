package com.sebasmalparqueado.cowsvsclown.cows.mapper;

import com.sebasmalparqueado.cowsvsclown.clowns.mapper.ClownMapper;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.mapper.OwnerMapper;

import java.util.List;

/**
 * Translates between the {@link Cow} entity and its DTOs.
 *
 * <p>The reason this layer exists: the entity has lazy relationships and
 * circular references (cow -> clowns -> cows). If the controller returned
 * the entity directly, Jackson would traverse those references and produce an
 * infinite JSON, as well as trigger queries outside the transaction.</p>
 *
 * <p>It is a utility class: private constructor and static methods, it's not
 * a Spring bean because it doesn't need to inject anything.</p>
 */
public final class CowMapper {

    private CowMapper() {
        // Utility class: not instantiated.
    }

    /**
     * Builds the entity with the cow's own data.
     *
     * <p>The owner and clowns are NOT resolved here: they are entities that must
     * be fetched from the database, and the mapper doesn't have repositories.
     * {@code CowService} takes care of that, as it can validate they exist.</p>
     */
    public static Cow toEntity(CowRequest request) {
        if (request == null) return null;
        return Cow.builder()
                .name(request.name())
                .weight(request.weight())
                .milkperday(request.milkperday())
                .active(true)
                .build();
    }

    /** Same as above, for cows that arrive nested in an owner. */
    public static Cow toEntity(OwnerCowRequest request) {
        if (request == null) return null;
        return Cow.builder()
                .name(request.name())
                .weight(request.weight())
                .milkperday(request.milkperday())
                .active(true)
                .build();
    }

    /**
     * Converts the entity into the full response.
     *
     * <p>Warning: must be called <b>inside</b> the transaction, because here
     * {@code getOwner()} and {@code getClowns()} are accessed, which are LAZY. If
     * called after closed, Hibernate would throw LazyInitializationException.</p>
     */
    public static CowResponse toResponse(Cow cow) {
        if (cow == null) return null;
        return new CowResponse(
                cow.getId(),
                cow.getName(),
                cow.getWeight(),
                cow.getMilkperday(),
                cow.isActive(),
                OwnerMapper.toSummary(cow.getOwner()),
                // Only active clowns are shown: logically deleted ones
                // remain in the join table but are not exposed.
                cow.getClowns().stream()
                        .filter(clown -> clown.isActive())
                        .map(ClownMapper::toSummary)
                        .toList()
        );
    }

    /** Short version, for when the cow appears inside an owner or clown. */
    public static CowSummaryResponse toSummary(Cow cow) {
        if (cow == null) return null;
        return new CowSummaryResponse(
                cow.getId(),
                cow.getName(),
                cow.getMilkperday()
        );
    }

    /** Converts a full list, skipping logically deleted cows. */
    public static List<CowSummaryResponse> toActiveSummaries(List<Cow> cows) {
        if (cows == null) return List.of();
        return cows.stream()
                .filter(Cow::isActive)
                .map(CowMapper::toSummary)
                .toList();
    }
}
