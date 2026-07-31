package com.sebasmalparqueado.cowsvsclown.cows.service;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.clowns.repository.IClownRepository;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.BadRequestException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import com.sebasmalparqueado.cowsvsclown.owner.service.OwnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cow business rules. This is the service where the two
 * relationships are seen working together:
 *
 * <ul>
 *   <li><b>1 to N</b> with the owner, when creating ({@link #create}) and when
 *       transferring a cow to another owner ({@link #changeOwner}).</li>
 *   <li><b>N to M</b> with the clowns, when the creation request brings
 *       {@code clownIds}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CowService {

    private final ICowRepository cowRepository;
    private final IClownRepository clownRepository;

    /**
     * The owner service is injected instead of the repository to reuse its
     * "find active or throw 404" logic and not repeat that rule here.
     */
    private final OwnerService ownerService;

    // ============================== Read ===============================

    /** All active cows, with owner and clowns. */
    @Transactional(readOnly = true)
    public List<CowResponse> getCows() {
        return cowRepository.findAllActiveWithRelations()
                .stream()
                .map(CowMapper::toResponse)
                .toList();
    }

    /** An active cow by id. Throws 404 if not found. */
    @Transactional(readOnly = true)
    public CowResponse getById(UUID id) {
        Cow cow = cowRepository.findActiveWithRelationsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));
        return CowMapper.toResponse(cow);
    }

    /**
     * Cows of an owner. Before querying we verify that the owner exists,
     * to be able to respond 404 instead of an empty list that says nothing.
     */
    @Transactional(readOnly = true)
    public List<CowResponse> getByOwner(Long ownerId) {
        ownerService.getActiveEntityOrThrow(ownerId);

        return cowRepository.findAllByOwnerIdAndActiveTrueOrderByNameAsc(ownerId)
                .stream()
                .map(CowMapper::toResponse)
                .toList();
    }

    /** Finds a cow by exact name using the native SQL query. */
    @Transactional(readOnly = true)
    public CowResponse getByName(String name) {
        Cow cow = cowRepository.findByNameSQL(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "There is no cow named '%s'".formatted(name)));
        return CowMapper.toResponse(cow);
    }

    // ============================= Write ==============================

    /**
     * Creates a cow resolving both of its relationships.
     *
     * <p><b>1 to N:</b> the owner is found by {@code ownerId} and assigned with
     * {@code owner.addCow(cow)}, which keeps both sides synchronized. Without
     * an owner it cannot be created, because owner_id is NOT NULL.</p>
     *
     * <p><b>N to M:</b> if {@code clownIds} are provided, the cow is assigned to each
     * clown. This goes <b>after</b> the save: the cow needs to have its id
     * generated before the row can be inserted in the join table.</p>
     */
    @Transactional
    public CowResponse create(CowRequest request) {
        if (cowRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException(
                    "There is already a cow named '%s'".formatted(request.name()));
        }

        // --- 1 to N relationship: the cow is born linked to an existing owner ---
        Owner owner = ownerService.getActiveEntityOrThrow(request.ownerId());

        Cow cow = CowMapper.toEntity(request);
        owner.addCow(cow);

        Cow saved = cowRepository.save(cow);

        // --- N to M relationship: assignment to the indicated clowns ---
        assignClowns(saved, request.clownIdsOrEmpty());

        log.info("Cow created id={} owner={} clowns={}",
                saved.getId(), owner.getId(), request.clownIdsOrEmpty().size());

        return CowMapper.toResponse(saved);
    }

    /**
     * Updates the cow's own data. Fields in null are not touched
     * (PATCH semantics). The owner and clowns have separate endpoints.
     */
    @Transactional
    public CowResponse update(UUID id, CowUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException(
                    "The request has no fields to update");
        }

        Cow cow = cowRepository.findActiveWithRelationsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));

        if (request.name() != null) {
            // AndIdNot: if the name is not changed, the cow shouldn't collide with itself.
            if (cowRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
                throw new ConflictException(
                        "There is already another cow named '%s'".formatted(request.name()));
            }
            cow.setName(request.name());
        }
        if (request.weight() != null) {
            cow.setWeight(request.weight());
        }
        if (request.milkperday() != null) {
            cow.setMilkperday(request.milkperday());
        }

        Cow updated = cowRepository.save(cow);
        log.info("Cow updated id={}", id);
        return CowMapper.toResponse(updated);
    }

    /**
     * <b>1 to N insertion on an already existing cow:</b> changes its owner.
     *
     * <p>The only thing that updates the owner_id column is {@code cow.setOwner()},
     * because the owner side of the relationship is the {@code @ManyToOne}. Both
     * lists in memory are still adjusted so that an object already loaded in this
     * transaction does not become outdated.</p>
     */
    @Transactional
    public CowResponse changeOwner(UUID cowId, Long newOwnerId) {
        Cow cow = cowRepository.findActiveWithRelationsById(cowId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", cowId));

        Owner newOwner = ownerService.getActiveEntityOrThrow(newOwnerId);
        Owner currentOwner = cow.getOwner();

        if (currentOwner != null && currentOwner.getId().equals(newOwnerId)) {
            throw new ConflictException(
                    "The cow '%s' already belongs to %s"
                            .formatted(cow.getName(), newOwner.getFullName()));
        }

        /*
         * It is removed from the previous owner's list with a direct remove and NOT with
         * owner.removeCow(), because that helper also does cow.setOwner(null) and
         * the relationship has orphanRemoval = true: Hibernate would interpret that the
         * cow became an orphan and would delete it from the database upon commit.
         */
        if (currentOwner != null) {
            currentOwner.getCows().remove(cow);
        }
        newOwner.addCow(cow);

        Cow updated = cowRepository.save(cow);
        log.info("Cow id={} transferred to owner id={}", cowId, newOwnerId);
        return CowMapper.toResponse(updated);
    }

    /**
     * Logical delete of the cow: {@code active = false}, the row stays.
     *
     * <p>The rows in the clown_cow join table are not touched: this preserves
     * the history of which clown took care of it. Since the mappers filter by
     * active, the cow stops appearing in the response of those clowns anyway.</p>
     */
    @Transactional
    public void softDelete(UUID id) {
        Cow cow = cowRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));

        cow.setActive(false);
        cowRepository.save(cow);
        log.info("Cow logically deleted id={}", id);
    }

    // ============================= Utilities ============================

    /**
     * Assigns the cow to each clown in the list (N to M relationship).
     *
     * <p>The assignment is done with {@code clown.addCow(cow)} and the clown is
     * saved, not the cow: the owner side of the relationship is Clown, who holds
     * the {@code @JoinTable}. Doing it the other way around would not insert anything
     * in clown_cow.</p>
     */
    private void assignClowns(Cow cow, List<UUID> clownIds) {
        if (clownIds.isEmpty()) {
            return;
        }

        for (UUID clownId : clownIds) {
            Clown clown = clownRepository.findByIdAndActiveTrue(clownId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

            // Ignores duplicates within the same request instead of failing:
            // the result requested by the client is fulfilled anyway.
            if (!clown.hasCow(cow)) {
                clown.addCow(cow);
                clownRepository.save(clown);
            }
        }
    }
}
