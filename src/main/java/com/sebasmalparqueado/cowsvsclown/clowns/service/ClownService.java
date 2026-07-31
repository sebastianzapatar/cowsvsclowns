package com.sebasmalparqueado.cowsvsclown.clowns.service;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.mapper.ClownMapper;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.BadRequestException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reglas de negocio de los payasos y, sobre todo, el lugar donde se administra
 * la relación <b>N a M</b> con las vacas.
 *
 * <p>Todas las asignaciones vaca-payaso se hacen desde acá por una razón de
 * JPA: {@link Clown} es el lado dueño de la relación (el que declara la
 * {@code @JoinTable}), y Hibernate solo mira la lista de ese lado para decidir
 * qué filas insertar o borrar en la tabla intermedia clown_cow. Modificar
 * {@code cow.getClowns()} no persistiría nada.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClownService {

    private final IClownRepository clownRepository;
    private final ICowRepository cowRepository;

    // ============================== Lectura ===============================

    /** Todos los payasos activos, con sus vacas. */
    @Transactional(readOnly = true)
    public List<ClownResponse> getAll() {
        return clownRepository.findAllActiveWithCows()
                .stream()
                .map(ClownMapper::toResponse)
                .toList();
    }

    /** Un payaso activo por id. Lanza 404 si no está. */
    @Transactional(readOnly = true)
    public ClownResponse getById(UUID id) {
        Clown clown = clownRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));
        return ClownMapper.toResponse(clown);
    }

    /**
     * Payasos que cuidan una vaca: la relación N a M leída desde el otro lado.
     * Se valida primero que la vaca exista para poder responder 404.
     */
    @Transactional(readOnly = true)
    public List<ClownResponse> getByCow(UUID cowId) {
        findActiveCowOrThrow(cowId);

        return clownRepository.findActiveByCowId(cowId)
                .stream()
                .map(ClownMapper::toResponse)
                .toList();
    }

    // ============================= Escritura ==============================

    /**
     * Crea un payaso y, si la petición trae {@code cowIds}, le asigna esas
     * vacas de una vez (inserción N a M).
     *
     * <p>Las vacas se agregan <b>antes</b> del save: como Clown es el lado
     * dueño, al guardarlo Hibernate inserta el payaso y en el mismo commit las
     * filas de clown_cow.</p>
     */
    @Transactional
    public ClownResponse create(ClownRequest request) {
        if (clownRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException(
                    "Ya existe un payaso llamado '%s'".formatted(request.name()));
        }

        Clown clown = ClownMapper.toEntity(request);

        for (UUID cowId : request.cowIdsOrEmpty()) {
            Cow cow = findActiveCowOrThrow(cowId);
            // Ignora ids repetidos dentro de la misma petición: el resultado
            // que pidió el cliente se cumple igual.
            if (!clown.hasCow(cow)) {
                clown.addCow(cow);
            }
        }

        Clown saved = clownRepository.save(clown);

        log.info("Payaso creado id={} con {} vaca(s) asignada(s)",
                saved.getId(), saved.getCows().size());

        return ClownMapper.toResponse(saved);
    }

    /**
     * Actualiza nombre y/o descripción. Los campos en null no se tocan
     * (semántica de PATCH). Las vacas asignadas se manejan con
     * {@link #assignCow} y {@link #unassignCow}.
     */
    @Transactional
    public ClownResponse update(UUID id, ClownUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException(
                    "La petición no trae ningún campo para actualizar");
        }

        Clown clown = clownRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));

        if (request.name() != null) {
            // AndIdNot: si no se cambia el nombre, el payaso no debe chocar consigo mismo.
            if (clownRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
                throw new ConflictException(
                        "Ya existe otro payaso llamado '%s'".formatted(request.name()));
            }
            clown.setName(request.name());
        }
        if (request.description() != null) {
            clown.setDescription(request.description());
        }

        Clown updated = clownRepository.save(clown);
        log.info("Payaso actualizado id={}", id);
        return ClownMapper.toResponse(updated);
    }

    /**
     * <b>Inserción N a M:</b> asigna una vaca existente a un payaso existente.
     * Es una fila nueva en la tabla intermedia clown_cow.
     */
    @Transactional
    public ClownResponse assignCow(UUID clownId, UUID cowId) {
        Clown clown = clownRepository.findActiveWithCowsById(clownId)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

        Cow cow = findActiveCowOrThrow(cowId);

        // Acá sí se responde 409 y no se ignora en silencio: el cliente pidió
        // explícitamente crear una asignación que ya existía.
        if (clown.hasCow(cow)) {
            throw new ConflictException(
                    "La vaca '%s' ya está asignada al payaso '%s'"
                            .formatted(cow.getName(), clown.getName()));
        }

        clown.addCow(cow);
        Clown updated = clownRepository.save(clown);

        log.info("Vaca id={} asignada al payaso id={}", cowId, clownId);
        return ClownMapper.toResponse(updated);
    }

    /**
     * Quita la asignación entre un payaso y una vaca: borra la fila de
     * clown_cow.
     *
     * <p>Este sí es un borrado físico, y está bien que lo sea: la tabla
     * intermedia no guarda datos propios, solo representa el vínculo. Ni la
     * vaca ni el payaso se tocan.</p>
     */
    @Transactional
    public ClownResponse unassignCow(UUID clownId, UUID cowId) {
        Clown clown = clownRepository.findActiveWithCowsById(clownId)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

        Cow cow = findActiveCowOrThrow(cowId);

        if (!clown.hasCow(cow)) {
            throw new ConflictException(
                    "La vaca '%s' no está asignada al payaso '%s'"
                            .formatted(cow.getName(), clown.getName()));
        }

        clown.removeCow(cow);
        Clown updated = clownRepository.save(clown);

        log.info("Vaca id={} desasignada del payaso id={}", cowId, clownId);
        return ClownMapper.toResponse(updated);
    }

    /**
     * Baja lógica del payaso.
     *
     * <p>Sus filas de clown_cow no se borran: el payaso desaparece de las
     * consultas porque todas filtran por activo, pero queda el registro de qué
     * vacas cuidaba.</p>
     */
    @Transactional
    public void softDelete(UUID id) {
        Clown clown = clownRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));

        clown.setActive(false);
        clownRepository.save(clown);
        log.info("Payaso dado de baja id={}", id);
    }

    // ============================= Utilitarios ============================

    /** Busca una vaca activa o lanza 404. Se repite en varios métodos de acá. */
    private Cow findActiveCowOrThrow(UUID cowId) {
        return cowRepository.findByIdAndActiveTrue(cowId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", cowId));
    }
}
