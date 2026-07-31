package com.sebasmalparqueado.cowsvsclown.owner.repository;

import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Owner}.
 *
 * <p>By extending {@code JpaRepository} we get save, findAll, count,
 * etc. for free. The methods here are resolved automatically by name (Spring Data
 * builds the query reading "findAllByActiveTrue..."), except for those with
 * {@code @Query}, which are written by hand to be able to use JOIN FETCH.</p>
 */
public interface IOwnerRepository extends JpaRepository<Owner, Long> {

    /**
     * An active owner by id. This is the findById used throughout the application:
     * the one from JpaRepository would also return the logically deleted ones.
     */
    Optional<Owner> findByIdAndActiveTrue(Long id);

    /** Is there already an active owner with that first and last name? Used to avoid duplicates. */
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
            String firstName, String lastName);

    /**
     * Same as above but excluding an id.
     * Used when updating: without the "AndIdNot", an owner would collide with themselves
     * when saving without changing their name.
     */
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
            String firstName, String lastName, Long id);

    /**
     * All active owners with their cows already loaded.
     *
     * <p>The LEFT JOIN FETCH brings owners and cows in a SINGLE query. Without this,
     * since the relationship is LAZY, the mapper would trigger a query for each owner
     * when asking for their cows: the N+1 problem.</p>
     *
     * <p>It is LEFT and not INNER so that owners without cows also appear.
     * DISTINCT is not necessary: Hibernate 6 already removes the duplicates generated
     * by the join when building the entities.</p>
     */
    @Query("""
            SELECT o FROM Owner o
            LEFT JOIN FETCH o.cows
            WHERE o.active = true
            ORDER BY o.lastName, o.firstName
            """)
    List<Owner> findAllActiveWithCows();

    /** An active owner with their cows already loaded, in a single query. */
    @Query("""
            SELECT o FROM Owner o
            LEFT JOIN FETCH o.cows
            WHERE o.id = :id AND o.active = true
            """)
    Optional<Owner> findActiveWithCowsById(@Param("id") Long id);
}
