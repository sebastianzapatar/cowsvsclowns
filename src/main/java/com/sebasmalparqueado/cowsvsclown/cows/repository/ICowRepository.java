package com.sebasmalparqueado.cowsvsclown.cows.repository;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de {@link Cow}.
 *
 * <p>De {@code JpaRepository} ya se heredan save, findAll, count y compañía.
 * Acá solo se agregan las consultas propias: las que filtran por el borrado
 * lógico y las que traen las relaciones cargadas de una.</p>
 */
public interface ICowRepository extends JpaRepository<Cow, UUID> {

    /** Una vaca activa por id (el findById heredado también trae las de baja). */
    Optional<Cow> findByIdAndActiveTrue(UUID id);

    /** Búsqueda por nombre exacto, sin distinguir mayúsculas. */
    Optional<Cow> findByNameIgnoreCaseAndActiveTrue(String name);

    /**
     * ¿Ya existe una vaca con ese nombre?
     *
     * <p>A propósito NO filtra por activo: la columna name tiene un UNIQUE en
     * la base, y ese índice también cuenta las filas dadas de baja lógicamente.
     * Si acá se filtrara por activas, el servicio dejaría pasar el nombre y
     * después reventaría el insert contra la restricción de Postgres.</p>
     */
    boolean existsByNameIgnoreCase(String name);

    /** Igual que el anterior, excluyendo un id: se usa al actualizar. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Vacas activas de un dueño. Es la consulta del lado "N" de la relación
     * 1 a N: se navega por {@code owner.id} y Spring Data lo traduce a un
     * WHERE sobre la columna owner_id.
     */
    List<Cow> findAllByOwnerIdAndActiveTrueOrderByNameAsc(Long ownerId);

    /**
     * Todas las vacas activas con dueño y payasos ya cargados.
     *
     * <p>Los dos JOIN FETCH resuelven el N+1: sin ellos, el mapper pediría el
     * dueño y los payasos de cada vaca por separado y se dispararían 2N
     * consultas extra.</p>
     *
     * <p>Solo se puede hacer FETCH de UNA colección tipo lista por consulta: si
     * se agregara otra, Hibernate falla con MultipleBagFetchException.
     * {@code owner} no cuenta porque es un @ManyToOne, no una colección.</p>
     */
    @Query("""
            SELECT c FROM Cow c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.clowns
            WHERE c.active = true
            ORDER BY c.name
            """)
    List<Cow> findAllActiveWithRelations();

    /** Una vaca activa con dueño y payasos ya cargados, en una sola consulta. */
    @Query("""
            SELECT c FROM Cow c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.clowns
            WHERE c.id = :id AND c.active = true
            """)
    Optional<Cow> findActiveWithRelationsById(@Param("id") UUID id);

    /**
     * La misma búsqueda por nombre pero en SQL nativo, como ejemplo de
     * {@code nativeQuery = true}.
     *
     * <p>Diferencias con JPQL que hay que tener presentes:</p>
     * <ul>
     *   <li>Se escriben nombres de TABLA y COLUMNA reales (cows, milkperday),
     *       no los de la clase Java.</li>
     *   <li>Va {@code SELECT *} y no {@code SELECT c}: el alias de entidad es
     *       cosa de JPQL.</li>
     *   <li>LIMIT es sintaxis de PostgreSQL. Si mañana se cambia de motor, esta
     *       consulta hay que reescribirla; las JPQL no.</li>
     * </ul>
     */
    @Query(
            value = """
                    SELECT * FROM cows
                    WHERE LOWER(name) = LOWER(:name) AND active = true
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<Cow> findByNameSQL(@Param("name") String name);
}
