package com.sebasmalparqueado.cowsvsclown.clowns.mapper;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;

import java.util.List;

/**
 * Traduce entre la entidad {@link Clown} y sus DTO. Misma idea que
 * {@code CowMapper}: clase de utilidad, sin estado.
 */
public final class ClownMapper {

    private ClownMapper() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Arma el payaso con sus datos propios.
     *
     * <p>Las vacas de {@code cowIds} no se resuelven acá: hay que buscarlas en
     * la base y verificar que existan, y eso es trabajo de {@code ClownService}.</p>
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
     * Convierte a la respuesta completa, con las vacas resumidas.
     * Hay que llamarlo dentro de la transacción: {@code getCows()} es LAZY.
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

    /** Versión corta, para cuando el payaso aparece dentro de una vaca. */
    public static ClownSummaryResponse toSummary(Clown clown) {
        if (clown == null) return null;
        return new ClownSummaryResponse(
                clown.getId(),
                clown.getName()
        );
    }
}
