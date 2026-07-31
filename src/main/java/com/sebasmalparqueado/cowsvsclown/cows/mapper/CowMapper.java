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
 * Traduce entre la entidad {@link Cow} y sus DTO.
 *
 * <p>La razón de que exista esta capa: la entidad tiene relaciones perezosas y
 * referencias circulares (vaca -> payasos -> vacas). Si el controller devolviera
 * la entidad directamente, Jackson recorrería esas referencias y produciría un
 * JSON infinito, además de disparar consultas fuera de la transacción.</p>
 *
 * <p>Es una clase de utilidad: constructor privado y métodos estáticos, no es
 * un bean de Spring porque no necesita inyectar nada.</p>
 */
public final class CowMapper {

    private CowMapper() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Arma la entidad con los datos propios de la vaca.
     *
     * <p>El dueño y los payasos NO se resuelven acá: son entidades que hay que
     * ir a buscar a la base, y el mapper no tiene repositorios. De eso se
     * encarga {@code CowService}, que sí puede validar que existan.</p>
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

    /** Igual que el anterior, para las vacas que llegan anidadas en un dueño. */
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
     * Convierte la entidad en la respuesta completa.
     *
     * <p>Ojo: hay que llamarlo <b>dentro</b> de la transacción, porque acá se
     * tocan {@code getOwner()} y {@code getClowns()}, que son LAZY. Si se
     * llamara después de cerrada, Hibernate lanzaría LazyInitializationException.</p>
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
                // Solo se muestran los payasos vigentes: los dados de baja
                // siguen en la tabla intermedia pero no se exponen.
                cow.getClowns().stream()
                        .filter(clown -> clown.isActive())
                        .map(ClownMapper::toSummary)
                        .toList()
        );
    }

    /** Versión corta, para cuando la vaca aparece dentro de un dueño o payaso. */
    public static CowSummaryResponse toSummary(Cow cow) {
        if (cow == null) return null;
        return new CowSummaryResponse(
                cow.getId(),
                cow.getName(),
                cow.getMilkperday()
        );
    }

    /** Convierte una lista completa, saltándose las vacas dadas de baja. */
    public static List<CowSummaryResponse> toActiveSummaries(List<Cow> cows) {
        if (cows == null) return List.of();
        return cows.stream()
                .filter(Cow::isActive)
                .map(CowMapper::toSummary)
                .toList();
    }
}
