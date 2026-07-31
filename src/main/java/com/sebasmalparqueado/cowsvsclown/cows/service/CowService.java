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
 * Reglas de negocio de las vacas. Es el servicio donde se ven las dos
 * relaciones funcionando juntas:
 *
 * <ul>
 *   <li><b>1 a N</b> con el dueño, al crear ({@link #create}) y al traspasar
 *       una vaca de dueño ({@link #changeOwner}).</li>
 *   <li><b>N a M</b> con los payasos, cuando la petición de creación trae
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
     * Se inyecta el servicio y no el repositorio de dueños para reutilizar su
     * "buscar activo o lanzar 404" y no repetir esa regla acá.
     */
    private final OwnerService ownerService;

    // ============================== Lectura ===============================

    /** Todas las vacas activas, con dueño y payasos. */
    @Transactional(readOnly = true)
    public List<CowResponse> getCows() {
        return cowRepository.findAllActiveWithRelations()
                .stream()
                .map(CowMapper::toResponse)
                .toList();
    }

    /** Una vaca activa por id. Lanza 404 si no está. */
    @Transactional(readOnly = true)
    public CowResponse getById(UUID id) {
        Cow cow = cowRepository.findActiveWithRelationsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));
        return CowMapper.toResponse(cow);
    }

    /**
     * Vacas de un dueño. Antes de consultar se verifica que el dueño exista,
     * para poder responder 404 en vez de una lista vacía que no dice nada.
     */
    @Transactional(readOnly = true)
    public List<CowResponse> getByOwner(Long ownerId) {
        ownerService.getActiveEntityOrThrow(ownerId);

        return cowRepository.findAllByOwnerIdAndActiveTrueOrderByNameAsc(ownerId)
                .stream()
                .map(CowMapper::toResponse)
                .toList();
    }

    /** Busca una vaca por nombre exacto usando la consulta en SQL nativo. */
    @Transactional(readOnly = true)
    public CowResponse getByName(String name) {
        Cow cow = cowRepository.findByNameSQL(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una vaca llamada '%s'".formatted(name)));
        return CowMapper.toResponse(cow);
    }

    // ============================= Escritura ==============================

    /**
     * Crea una vaca resolviendo sus dos relaciones.
     *
     * <p><b>1 a N:</b> se busca el dueño por {@code ownerId} y se asigna con
     * {@code owner.addCow(cow)}, que deja sincronizados los dos lados. Sin
     * dueño no se puede crear, porque owner_id es NOT NULL.</p>
     *
     * <p><b>N a M:</b> si vienen {@code clownIds}, se asigna la vaca a cada
     * payaso. Esto va <b>después</b> del save: la vaca necesita tener su id
     * generado antes de poder insertar la fila en la tabla intermedia.</p>
     */
    @Transactional
    public CowResponse create(CowRequest request) {
        if (cowRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException(
                    "Ya existe una vaca llamada '%s'".formatted(request.name()));
        }

        // --- Relación 1 a N: la vaca nace colgada de un dueño existente ---
        Owner owner = ownerService.getActiveEntityOrThrow(request.ownerId());

        Cow cow = CowMapper.toEntity(request);
        owner.addCow(cow);

        Cow saved = cowRepository.save(cow);

        // --- Relación N a M: asignación a los payasos indicados ---
        assignClowns(saved, request.clownIdsOrEmpty());

        log.info("Vaca creada id={} dueño={} payasos={}",
                saved.getId(), owner.getId(), request.clownIdsOrEmpty().size());

        return CowMapper.toResponse(saved);
    }

    /**
     * Actualiza los datos propios de la vaca. Los campos en null no se tocan
     * (semántica de PATCH). El dueño y los payasos tienen endpoints aparte.
     */
    @Transactional
    public CowResponse update(UUID id, CowUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException(
                    "La petición no trae ningún campo para actualizar");
        }

        Cow cow = cowRepository.findActiveWithRelationsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));

        if (request.name() != null) {
            // AndIdNot: si no se cambia el nombre, la vaca no debe chocar consigo misma.
            if (cowRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
                throw new ConflictException(
                        "Ya existe otra vaca llamada '%s'".formatted(request.name()));
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
        log.info("Vaca actualizada id={}", id);
        return CowMapper.toResponse(updated);
    }

    /**
     * <b>Inserción 1 a N sobre una vaca que ya existe:</b> le cambia el dueño.
     *
     * <p>Lo único que actualiza la columna owner_id es {@code cow.setOwner()},
     * porque el lado dueño de la relación es el {@code @ManyToOne}. Igual se
     * ajustan las dos listas en memoria para que un objeto ya cargado en esta
     * transacción no quede desactualizado.</p>
     */
    @Transactional
    public CowResponse changeOwner(UUID cowId, Long newOwnerId) {
        Cow cow = cowRepository.findActiveWithRelationsById(cowId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", cowId));

        Owner newOwner = ownerService.getActiveEntityOrThrow(newOwnerId);
        Owner currentOwner = cow.getOwner();

        if (currentOwner != null && currentOwner.getId().equals(newOwnerId)) {
            throw new ConflictException(
                    "La vaca '%s' ya pertenece a %s"
                            .formatted(cow.getName(), newOwner.getFullName()));
        }

        /*
         * Se saca de la lista del dueño anterior con un remove directo y NO con
         * owner.removeCow(), porque ese helper además hace cow.setOwner(null) y
         * la relación tiene orphanRemoval = true: Hibernate interpretaría que la
         * vaca quedó huérfana y la borraría de la base al hacer commit.
         */
        if (currentOwner != null) {
            currentOwner.getCows().remove(cow);
        }
        newOwner.addCow(cow);

        Cow updated = cowRepository.save(cow);
        log.info("Vaca id={} traspasada al dueño id={}", cowId, newOwnerId);
        return CowMapper.toResponse(updated);
    }

    /**
     * Baja lógica de la vaca: {@code active = false}, la fila se queda.
     *
     * <p>Las filas de la tabla intermedia clown_cow no se tocan: así se conserva
     * el histórico de qué payaso la cuidaba. Como los mappers filtran por
     * activo, la vaca igual deja de aparecer en la respuesta de esos payasos.</p>
     */
    @Transactional
    public void softDelete(UUID id) {
        Cow cow = cowRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cow", id));

        cow.setActive(false);
        cowRepository.save(cow);
        log.info("Vaca dada de baja id={}", id);
    }

    // ============================= Utilitarios ============================

    /**
     * Asigna la vaca a cada payaso de la lista (relación N a M).
     *
     * <p>La asignación se hace con {@code clown.addCow(cow)} y se guarda el
     * payaso, no la vaca: el lado dueño de la relación es Clown, que es quien
     * tiene la {@code @JoinTable}. Hacerlo al revés no insertaría nada en
     * clown_cow.</p>
     */
    private void assignClowns(Cow cow, List<UUID> clownIds) {
        if (clownIds.isEmpty()) {
            return;
        }

        for (UUID clownId : clownIds) {
            Clown clown = clownRepository.findByIdAndActiveTrue(clownId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Clown", clownId));

            // Ignora repetidos dentro de la misma petición en vez de fallar:
            // el resultado que pidió el cliente igual se cumple.
            if (!clown.hasCow(cow)) {
                clown.addCow(cow);
                clownRepository.save(clown);
            }
        }
    }
}
