package com.sebasmalparqueado.cowsvsclown.owner.mapper;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerSummaryResponse;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;

import java.util.List;

/**
 * Traduce entre la entidad {@link Owner} y sus DTO. Misma idea que
 * {@code CowMapper}: clase de utilidad, sin estado.
 */
public final class OwnerMapper {

    private OwnerMapper() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Arma el dueño y, si la petición trae vacas, se las cuelga con
     * {@link Owner#addCow(Cow)}.
     *
     * <p>Acá está la inserción 1 a N en cascada: gracias al
     * {@code cascade = CascadeType.ALL} del {@code @OneToMany}, guardar el
     * dueño alcanza para que Hibernate inserte también todas sus vacas con el
     * owner_id ya puesto, en una sola transacción.</p>
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
     * Convierte a la respuesta completa, con solo los nombres de las vacas activas.
     * Hay que llamarlo dentro de la transacción: {@code getCows()} es LAZY.
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

    /** Versión corta, para cuando el dueño aparece dentro de una vaca. */
    public static OwnerSummaryResponse toSummary(Owner owner) {
        if (owner == null) return null;
        return new OwnerSummaryResponse(
                owner.getId(),
                owner.getFullName()
        );
    }
}
