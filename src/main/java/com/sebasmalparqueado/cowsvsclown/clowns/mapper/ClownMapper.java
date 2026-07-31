package com.sebasmalparqueado.cowsvsclown.clowns.mapper;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;

import java.util.List;

/**
 * Translates between the {@link Clown} entity and its DTOs. Same idea as
 * {@code CowMapper}: utility class, stateless.
 */
public final class ClownMapper {

    private ClownMapper() {
        // Utility class: not instantiated.
    }

    /**
     * Builds the clown with its own data.
     *
     * <p>The cows from {@code cowIds} are not resolved here: they must be
     * looked up in the database and verified to exist, and that is the job
     * of {@code ClownService}.</p>
     */
    public static Clown toEntity(ClownRequest request) {
        if (request == null) return null;
        return Clown.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();
    }

    /**
     * Converts to the full response, with summarized cows.
     * Must be called within a transaction: {@code getCows()} is LAZY.
     */
    public static ClownResponse toResponse(Clown clown) {
        if (clown == null) return null;

        List<CowSummaryResponse> cows = CowMapper.toActiveSummaries(clown.getCows());

        return new ClownResponse(
                clown.getId(),
                clown.getName(),
                clown.getDescription(),
                clown.isActive(),
                cows.size(),
                cows
        );
    }

    /** Short version, for when the clown appears inside a cow. */
    public static ClownSummaryResponse toSummary(Clown clown) {
        if (clown == null) return null;
        return new ClownSummaryResponse(
                clown.getId(),
                clown.getName()
        );
    }
}
