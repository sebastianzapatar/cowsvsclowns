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
 * Reglas de negocio de los dueños.
 *
 * <p>El servicio es el único que decide: valida, coordina repositorios y arma
 * la respuesta. El controller solo recibe HTTP y el repositorio solo habla con
 * la base; ninguno de los dos toma decisiones.</p>
 *
 * <p>Todos los métodos son transaccionales. Los de lectura llevan
 * {@code readOnly = true} (le avisa a Hibernate que no revise cambios al
 * cerrar, y a Postgres que la transacción no escribe). Los de escritura llevan
 * {@code @Transactional} a secas: si algo falla a mitad, se deshace todo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final IOwnerRepository ownerRepository;

    /**
     * Solo se usa para validar que no se repitan nombres de vacas cuando llegan
     * anidadas en el dueño. Las vacas se guardan por cascada, no con este repo.
     */
    private final ICowRepository cowRepository;

    // ============================== Lectura ===============================

    /** Todos los dueños activos, cada uno con sus vacas. */
    @Transactional(readOnly = true)
    public List<OwnerResponse> getAll() {
        return ownerRepository.findAllActiveWithCows()
                .stream()
                .map(OwnerMapper::toResponse)
                .toList();
    }

    /** Un dueño activo por id. Lanza 404 si no está. */
    @Transactional(readOnly = true)
    public OwnerResponse getById(Long id) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));
        return OwnerMapper.toResponse(owner);
    }

    // ============================= Escritura ==============================

    /**
     * Crea un dueño y, si vienen en la petición, sus vacas de una vez.
     *
     * <p><b>Inserción 1 a N.</b> El {@code ownerRepository.save()} guarda el
     * dueño y, por el {@code cascade = ALL} del {@code @OneToMany}, también
     * cada vaca de la lista. Hibernate ordena solo los INSERT: primero el
     * dueño, para tener su id generado, y después las vacas con ese id ya
     * puesto en la columna owner_id.</p>
     */
    @Transactional
    public OwnerResponse create(OwnerRequest request) {
        validateOwnerIsNotDuplicated(request.firstName(), request.lastName(), null);
        validateCowNames(request.cowsOrEmpty());

        Owner owner = OwnerMapper.toEntity(request);
        Owner saved = ownerRepository.save(owner);

        log.info("Dueño creado id={} con {} vaca(s)", saved.getId(), saved.getCows().size());
        return OwnerMapper.toResponse(saved);
    }

    /**
     * Actualiza nombre y/o apellido. Los campos que llegan en null se dejan
     * como estaban: es la semántica de PATCH.
     */
    @Transactional
    public OwnerResponse update(Long id, OwnerUpdateRequest request) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));

        // Se calcula cómo quedaría el nombre completo para validar el duplicado
        // contra los valores nuevos, no contra los que todavía tiene la entidad.
        String nuevoNombre = request.firstName() != null
                ? request.firstName() : owner.getFirstName();
        String nuevoApellido = request.lastName() != null
                ? request.lastName() : owner.getLastName();

        validateOwnerIsNotDuplicated(nuevoNombre, nuevoApellido, id);

        owner.setFirstName(nuevoNombre);
        owner.setLastName(nuevoApellido);

        /*
         * No hace falta llamar a save(): la entidad está "managed" dentro de la
         * transacción, así que Hibernate detecta el cambio y hace el UPDATE al
         * hacer commit. Se deja el save() igual porque es más explícito de leer.
         */
        Owner updated = ownerRepository.save(owner);

        log.info("Dueño actualizado id={}", id);
        return OwnerMapper.toResponse(updated);
    }

    /**
     * Baja lógica del dueño.
     *
     * <p>Nunca se borra la fila: se marca {@code active = false}. Se hace lo
     * mismo con sus vacas, porque la columna owner_id es NOT NULL y una vaca no
     * puede quedar sin dueño. Si más adelante se quisiera conservar las vacas,
     * habría que traspasarlas antes con
     * {@code PATCH /api/cows/{id}/owner/{ownerId}}.</p>
     */
    @Transactional
    public void softDelete(Long id) {
        Owner owner = ownerRepository.findActiveWithCowsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));

        owner.setActive(false);
        owner.getCows().forEach(cow -> cow.setActive(false));

        ownerRepository.save(owner);
        log.info("Dueño dado de baja id={} junto con {} vaca(s)", id, owner.getCows().size());
    }

    // ============================ Validaciones ============================

    /**
     * No se permiten dos dueños activos con el mismo nombre y apellido.
     *
     * @param idAExcluir id del dueño que se está actualizando, o null si se
     *                   está creando. Sin esto, al actualizar sin cambiar el
     *                   nombre el dueño chocaría contra sí mismo.
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
                    "Ya existe un dueño llamado %s %s".formatted(firstName, lastName));
        }
    }

    /**
     * Revisa los nombres de las vacas que vienen anidadas: que no se repitan
     * entre ellas ni contra las que ya están en la base.
     *
     * <p>Se valida antes de guardar para poder devolver un 409 con un mensaje
     * claro. Si se dejara pasar, saltaría el UNIQUE de Postgres y el mensaje
     * sería mucho más críptico.</p>
     */
    private void validateCowNames(List<OwnerCowRequest> cows) {
        Set<String> vistos = new HashSet<>();

        for (OwnerCowRequest cow : cows) {
            String nombreNormalizado = cow.name().toLowerCase();

            if (!vistos.add(nombreNormalizado)) {
                throw new ConflictException(
                        "El nombre de vaca '%s' viene repetido en la petición"
                                .formatted(cow.name()));
            }
            if (cowRepository.existsByNameIgnoreCase(cow.name())) {
                throw new ConflictException(
                        "Ya existe una vaca llamada '%s'".formatted(cow.name()));
            }
        }
    }

    // ======================= Uso interno de otros servicios ===============

    /**
     * Devuelve la entidad Owner activa o lanza 404.
     *
     * <p>Lo usa {@code CowService} para resolver el dueño al crear o traspasar
     * una vaca. Devuelve la entidad y no el DTO justamente porque el que llama
     * necesita el objeto de JPA para armar la relación.</p>
     */
    @Transactional(readOnly = true)
    public Owner getActiveEntityOrThrow(Long id) {
        return ownerRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Owner", id));
    }
}
