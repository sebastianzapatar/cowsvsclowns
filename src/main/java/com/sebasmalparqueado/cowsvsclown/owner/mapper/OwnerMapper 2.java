package com.sebasmalparqueado.cowsvsclown.owner.mapper;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;

import java.util.List;

/**
 * Translates between the {@link Owner} entity and its DTOs. Same idea as
 * {@code CowMapper}: stateless utility class.
 */
public final class OwnerMapper {

    private OwnerMapper() {
        // Utility class: not instantiated.
    }

    /**
     * Builds the owner and, if the request brings cows, hangs them with
     * {@link Owner#addCow(Cow)}.
     *
     * <p>Here is the cascading 1 to N insertion: thanks to the
     * {@code cascade = CascadeType.ALL} of the {@code @OneToMany}, saving the
     * owner is enough for Hibernate to also insert all its cows with the
     * owner_id already set, in a single transaction.</p>
     */
    public static Owner toEntity(OwnerRequest request) {
        if (request == null) return null;

        Owner owner = Owner.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .active(true)
                .build();

        request.cowsOrEmpty().forEach(cowRequest ->
                owner.addCow(CowMapper.toEntity(cowRequest)));

        return owner;
    }

    /**
     * Converts to the full response, with only the names of the active cows.
     * It must be called within the transaction: {@code getCows()} is LAZY.
     */
    public static OwnerResponse toResponse(Owner owner) {
        if (owner == null) return null;

        List<String> cowNames = owner.getCows().stream()
                .filter(Cow::isActive)
                .map(Cow::getName)
                .toList();

        return new OwnerResponse(
                owner.getId(),
                owner.getFirstName(),
                owner.getLastName(),
                owner.getFullName(),
                owner.isActive(),
                cowNames.size(),
                cowNames
        );
    }

    /** Short version, for when the owner appears inside a cow. */
    public static OwnerSummaryResponse toSummary(Owner owner) {
        if (owner == null) return null;
        return new OwnerSummaryResponse(
                owner.getId(),
                owner.getFullName()
        );
    }
}
