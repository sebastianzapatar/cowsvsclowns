package com.sebasmalparqueado.cowsvsclown.owner.service;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ConflictException;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerCowRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import com.sebasmalparqueado.cowsvsclown.owner.mapper.OwnerMapper;
import com.sebasmalparqueado.cowsvsclown.owner.repository.IOwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owner business rules.
 *
 * <p>The service is the only one making decisions: it validates, coordinates repositories and builds
 * the response. The controller only receives HTTP and the repository only talks to
 * the database; neither makes decisions.</p>
 *
 * <p>All methods are transactional. Read methods carry
 * {@code readOnly = true} (tells Hibernate not to check for changes upon
 * closing, and Postgres that the transaction does not write). Write methods carry
 * plain {@code @Transactional}: if something fails midway, everything is rolled back.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final IOwnerRepository ownerRepository;

    /**
     * Only used to validate that cow names are not repeated when they arrive
     * nested in the owner. Cows are saved by cascade, not with this repo.
     */
    private final ICowRepository cowRepository;

    // ============================== Read ===============================

    /** All active owners, each with their cows. */
    @Transactional(readOnly = true)
    public List<OwnerResponse> getAll() {
        return ownerRepository.findAllActiveWithCows()
                .stream()
                .map(OwnerMapper::toResponse)
                .toList();
    }

    /** An active owner by id. Throws 404 if not found. */
    @Transactional(readOnly = true)
    public OwnerResponse getById(Long id) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() ->
                        ResourceNotFoundException.of
                                ("Owner", id));
        return OwnerMapper.toResponse(owner);
    }

    // ============================= Write ==============================

    /**
     * Creates an owner and, if they come in the request, their cows at once.
     *
     * <p><b>1 to N insertion.</b> The {@code ownerRepository.save()} saves the
     * owner and, because of the {@code cascade = ALL} of the {@code @OneToMany}, also
     * each cow in the list. Hibernate sorts the INSERTs alone: first the
     * owner, to have its id generated, and then the cows with that id already
     * set in the owner_id column.</p>
     */
    @Transactional
    public OwnerResponse create(OwnerRequest request) {
        validateOwnerIsNotDuplicated(request.firstName(), request.lastName(), null);
        validateCowNames(request.cowsOrEmpty());

        Owner owner = OwnerMapper.toEntity(request);
        Owner saved = ownerRepository.save(owner);

        log.info("Owner created id={} with {} cow(s)",
                saved.getId(), saved.getCows().size());
        return OwnerMapper.toResponse(saved);
    }

    /**
     * Updates first and/or last name. Fields arriving in null are left
     * as they were: this is PATCH semantics.
     */
    @Transactional
    public OwnerResponse update(Long id, OwnerUpdateRequest request) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() ->
                        ResourceNotFoundException.of("Owner", id));

        // The full name is calculated to validate the duplicate
        // against the new values, not against the ones the entity still has.
        String nuevoNombre = request.firstName() != null
                ? request.firstName() : owner.getFirstName();
        String nuevoApellido = request.lastName() != null
                ? request.lastName() : owner.getLastName();

        validateOwnerIsNotDuplicated(nuevoNombre, nuevoApellido, id);

        owner.setFirstName(nuevoNombre);
        owner.setLastName(nuevoApellido);

        /*
         * There is no need to call save(): the entity is "managed" inside the
         * transaction, so Hibernate detects the change and issues the UPDATE upon
         * commit. The save() is left anyway because it is more explicit to read.
         */
        Owner updated = ownerRepository.save(owner);

        log.info("Owner updated id={}", id);
        return OwnerMapper.toResponse(updated);
    }

    /**
     * Logical delete of the owner.
     *
     * <p>The row is never deleted: it is marked {@code active = false}. The same
     * is done with their cows, because the owner_id column is NOT NULL and a cow cannot
     * be left without an owner. If later one wanted to keep the cows,
     * they should be transferred beforehand with
     * {@code PATCH /api/cows/{id}/owner/{ownerId}}.</p>
     */
    @Transactional
    public void softDelete(Long id) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));

        owner.setActive(false);
        owner.getCows().forEach(cow -> cow.setActive(false));

        ownerRepository.save(owner);
        log.info("Owner logically deleted id={} along with {} cow(s)", id, owner.getCows().size());
    }

    // ============================ Validations ============================

    /**
     * Two active owners with the same first and last name are not allowed.
     *
     * @param idAExcluir id of the owner being updated, or null if being
     *                   created. Without this, when updating without changing the
     *                   name the owner would collide with themselves.
     */
    private void validateOwnerIsNotDuplicated(String firstName, String lastName, Long idAExcluir) {
        boolean existe = idAExcluir == null
                ? ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
                        firstName, lastName)
                : ownerRepository
                .existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
                        firstName, lastName, idAExcluir);

        if (existe) {
            throw new ConflictException(
                    "There is already an owner named %s %s".formatted(firstName, lastName));
        }
    }

    /**
     * Checks the names of the cows that come nested: that they do not repeat
     * among themselves nor against those already in the database.
     *
     * <p>It is validated before saving to be able to return a 409 with a clear
     * message. If allowed to pass, Postgres's UNIQUE constraint would trigger and the message
     * would be much more cryptic.</p>
     */
    private void validateCowNames(List<OwnerCowRequest> cows) {
        Set<String> vistos = new HashSet<>();

        for (OwnerCowRequest cow : cows) {
            String nombreNormalizado = cow.name().toLowerCase();

            if (!vistos.add(nombreNormalizado)) {
                throw new ConflictException(
                        "The cow name '%s' is repeated in the request"
                                .formatted(cow.name()));
            }
            if (cowRepository.existsByNameIgnoreCase(cow.name())) {
                throw new ConflictException(
                        "There is already a cow named '%s'".formatted(cow.name()));
            }
        }
    }

    // ======================= Internal use of other services ===============

    /**
     * Returns the active Owner entity or throws 404.
     *
     * <p>Used by {@code CowService} to resolve the owner when creating or transferring
     * a cow. Returns the entity and not the DTO precisely because the caller
     * needs the JPA object to build the relationship.</p>
     */
    @Transactional(readOnly = true)
    public Owner getActiveEntityOrThrow(Long id) {
        return ownerRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));
    }
}
