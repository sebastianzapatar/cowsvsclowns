package com.sebasmalparqueado.cowsvsclown.cows.repository;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Cow}.
 *
 * <p>From {@code JpaRepository} it already inherits save, findAll, count and so on.
 * Here we only add custom queries: those that filter by logical delete
 * and those that fetch relationships eagerly.</p>
 */
public interface ICowRepository extends JpaRepository<Cow, UUID> {

    /** An active cow by id (the inherited findById also brings deleted ones). */
    Optional<Cow> findByIdAndActiveTrue(UUID id);

    /** Exact search by name, ignoring case. */
    Optional<Cow> findByNameIgnoreCaseAndActiveTrue(String name);

    /**
     * Does a cow with that name already exist?
     *
     * <p>On purpose it DOES NOT filter by active: the name column has a UNIQUE
     * constraint in the database, and that index also counts logically deleted rows.
     * If we filtered by active here, the service would let the name pass and
     * then the insert would blow up against the Postgres constraint.</p>
     */
    boolean existsByNameIgnoreCase(String name);

    /** Same as above, excluding an id: used when updating. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Active cows of an owner. This is the "N" side query of the 1 to N
     * relationship: we navigate by {@code owner.id} and Spring Data translates
     * it to a WHERE on the owner_id column.
     */
    List<Cow> findAllByOwnerIdAndActiveTrueOrderByNameAsc(Long ownerId);

    /**
     * All active cows with owner and clowns already loaded.
     *
     * <p>The two JOIN FETCH resolve the N+1 problem: without them, the mapper
     * would ask for the owner and clowns of each cow separately and 2N
     * extra queries would be triggered.</p>
     *
     * <p>You can only FETCH ONE list-type collection per query: if
     * another one was added, Hibernate fails with MultipleBagFetchException.
     * {@code owner} doesn't count because it's a @ManyToOne, not a collection.</p>
     */
    @Query("""
            SELECT c FROM Cow c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.clowns
            WHERE c.active = true
            ORDER BY c.name
            """)
    List<Cow> findAllActiveWithRelations();

    /** An active cow with owner and clowns already loaded, in a single query. */
    @Query("""
            SELECT c FROM Cow c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.clowns
            WHERE c.id = :id AND c.active = true
            """)
    Optional<Cow> findActiveWithRelationsById(@Param("id") UUID id);

    /**
     * The same search by name but in native SQL, as an example of
     * {@code nativeQuery = true}.
     *
     * <p>Differences with JPQL to keep in mind:</p>
     * <ul>
     *   <li>You write real TABLE and COLUMN names (cows, milkperday),
     *       not the Java class names.</li>
     *   <li>It's {@code SELECT *} and not {@code SELECT c}: the entity alias
     *       is a JPQL thing.</li>
     *   <li>LIMIT is PostgreSQL syntax. If tomorrow the engine is changed, this
     *       query must be rewritten; JPQL ones do not.</li>
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
