package com.sebasmalparqueado.cowsvsclown.clowns.repository;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de {@link Clown}. Misma idea que {@code IOwnerRepository}:
 * consultas derivadas del nombre, y {@code @Query} solo donde hace falta un
 * JOIN FETCH para evitar el N+1.
 */
public interface IClownRepository extends JpaRepository<Clown, UUID> {

    /** Un payaso activo por id (el findById normal traería también los de baja). */
    Optional<Clown> findByIdAndActiveTrue(UUID id);

    /**
     * ¿Ya existe un payaso con ese nombre? No filtra por activo, por la misma
     * razón explicada en {@code ICowRepository.existsByNameIgnoreCase}: el
     * UNIQUE de la base también cuenta los registros dados de baja.
     */
    boolean existsByNameIgnoreCase(String name);

    /** Igual que el anterior, excluyendo un id: se usa al actualizar. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** Todos los payasos activos con sus vacas cargadas, en una sola consulta. */
    @Query("""
            SELECT c FROM Clown c
            LEFT JOIN FETCH c.cows
            WHERE c.active = true
            ORDER BY c.name
            """)
    List<Clown> findAllActiveWithCows();

    /** Un payaso activo con sus vacas cargadas, en una sola consulta. */
    @Query("""
            SELECT c FROM Clown c
            LEFT JOIN FETCH c.cows
            WHERE c.id = :id AND c.active = true
            """)
    Optional<Clown> findActiveWithCowsById(@Param("id") UUID id);

    /**
     * Payasos activos que tienen asignada una vaca.
     *
     * <p>El {@code JOIN c.cows cow} recorre la tabla intermedia clown_cow: es
     * la consulta que responde "¿quién cuida a esta vaca?" desde el otro lado
     * de la relación N a M.</p>
     */
    @Query("""
            SELECT c FROM Clown c
            JOIN c.cows cow
            WHERE cow.id = :cowId AND c.active = true
            ORDER BY c.name
            """)
    List<Clown> findActiveByCowId(@Param("cowId") UUID cowId);
}
