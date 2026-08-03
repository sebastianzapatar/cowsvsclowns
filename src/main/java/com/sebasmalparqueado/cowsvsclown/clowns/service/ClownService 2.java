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
 * Clown business rules and, most importantly, the place where the 
 * <b>N to M</b> relationship with cows is managed.
 *
 * <p>All cow-clown assignments are done here because of JPA: 
 * {@link Clown} is the owner side of the relationship (the one that declares
 * {@code @JoinTable}), and Hibernate only looks at the list on this side to decide
 * which rows to insert or delete in the join table clown_cow. Modifying
 * {@code cow.getClowns()} wouldn't persist anything.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClownService {

    private final IClownRepository clownRepository;
    private final ICowRepository cowRepository;

    // ============================== Read ===============================

    /** All active clowns, with their cows. */
    @Transactional(readOnly = true)
    public List<ClownResponse> getAll() {
        return clownRepository.findAllActiveWithCows()
                .stream()
                .map(ClownMapper::toResponse)
                .toList();
    }

    /** An active clown by id. Throws 404 if not found. */
    @Transactional(readOnly = true)
    public ClownResponse getById(UUID id) {
        Clown clown = clownRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));
        return ClownMapper.toResponse(clown);
    }

    /**
     * Clowns taking care of a cow: the N to M relationship read from the other side.
     * First validates that the cow exists to return a 404.
     */
    @Transactional(readOnly = true)
    public List<ClownResponse> getByCow(UUID cowId) {
        findActiveCowOrThrow(cowId);

        return clownRepository.findActiveByCowId(cowId)
                .stream()
                .map(ClownMapper::toResponse)
                .toList();
    }

    // ============================= Write ==============================

    /**
     * Creates a clown and, if the request brings {@code cowIds}, assigns those
     * cows at once (N to M insertion).
     *
     * <p>Cows are added <b>before</b> the save: since Clown is the owner side, 
     * when saving, Hibernate inserts the clown and in the same commit inserts
     * the rows in clown_cow.</p>
     */
    @Transactional
    public ClownResponse create(ClownRequest request) {
        if (clownRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException(
                    "There is already a clown named '%s'".formatted(request.name()));
        }

        Clown clown = ClownMapper.toEntity(request);

        for (UUID cowId : request.cowIdsOrEmpty()) {
            Cow cow = findActiveCowOrThrow(cowId);
            // Ignores duplicated ids within the same request: the result
            // requested by the client is still fulfilled.
            if (!clown.hasCow(cow)) {
                clown.addCow(cow);
            }
        }

        Clown saved = clownRepository.save(clown);

        log.info("Clown created id={} with {} assigned cow(s)",
                saved.getId(), saved.getCows().size());

        return ClownMapper.toResponse(saved);
    }

    /**
     * Updates name and/or description. Null fields are not touched
     * (PATCH semantics). Assigned cows are managed with
     * {@link #assignCow} and {@link #unassignCow}.
     */
    @Transactional
    public ClownResponse update(UUID id, ClownUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException(
                    "The request has no fields to update");
        }

        Clown clown = clownRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));

        if (request.name() != null) {
            // AndIdNot: if the name is not changed, the clown shouldn't collide with itself.
            if (clownRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
                throw new ConflictException(
                        "There is already another clown named '%s'".formatted(request.name()));
            }
            clown.setName(request.name());
        }
        if (request.description() != null) {
            clown.setDescription(request.description());
        }

        Clown updated = clownRepository.save(clown);
        log.info("Clown updated id={}", id);
        return ClownMapper.toResponse(updated);
    }

    /**
     * <b>N to M insertion:</b> assigns an existing cow to an existing clown.
     * It's a new row in the clown_cow join table.
     */
    @Transactional
    public ClownResponse assignCow(UUID clownId, UUID cowId) {
        Clown clown = clownRepository.findActiveWithCowsById(clownId)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

        Cow cow = findActiveCowOrThrow(cowId);

        // Here a 409 is responded and not silently ignored: the client explicitly
        // requested to create an assignment that already existed.
        if (clown.hasCow(cow)) {
            throw new ConflictException(
                    "The cow '%s' is already assigned to the clown '%s'"
                            .formatted(cow.getName(), clown.getName()));
        }

        clown.addCow(cow);
        Clown updated = clownRepository.save(clown);

        log.info("Cow id={} assigned to clown id={}", cowId, clownId);
        return ClownMapper.toResponse(updated);
    }

    /**
     * Removes the assignment between a clown and a cow: deletes the row in
     * clown_cow.
     *
     * <p>This IS a physical deletion, and it's fine: the join table doesn't
     * store its own data, it only represents the link. Neither the cow nor 
     * the clown is touched.</p>
     */
    @Transactional
    public ClownResponse unassignCow(UUID clownId, UUID cowId) {
        Clown clown = clownRepository.findActiveWithCowsById(clownId)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

        Cow cow = findActiveCowOrThrow(cowId);

        if (!clown.hasCow(cow)) {
            throw new ConflictException(
                    "The cow '%s' is not assigned to the clown '%s'"
                            .formatted(cow.getName(), clown.getName()));
        }

        clown.removeCow(cow);
        Clown updated = clownRepository.save(clown);

        log.info("Cow id={} unassigned from clown id={}", cowId, clownId);
        return ClownMapper.toResponse(updated);
    }

    /**
     * Clown logical delete.
     *
     * <p>Its rows in clown_cow are not deleted: the clown disappears from 
     * queries because they all filter by active, but the record of which 
     * cows it took care of remains.</p>
     */
    @Transactional
    public void softDelete(UUID id) {
        Clown clown = clownRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Clown", id));

        clown.setActive(false);
        clownRepository.save(clown);
        log.info("Clown logically deleted id={}", id);
    }

    // ============================= Utilities ============================

    /** Finds an active cow or throws 404. Repeated in several methods here. */
    private Cow findActiveCowOrThrow(UUID cowId) {
        return cowRepository.findByIdAndActiveTrue(cowId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", cowId));
    }
}
