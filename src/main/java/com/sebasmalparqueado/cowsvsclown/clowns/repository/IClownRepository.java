package com.sebasmalparqueado.cowsvsclown.clowns.repository;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clown Repository. Uses {@link JpaRepository} which brings all basic CRUD methods.
 */
public interface IClownRepository extends JpaRepository<Clown, UUID> {

    /** An active clown by id (the default findById also returns soft-deleted entities). */
    Optional<Clown> findByIdAndActiveTrue(UUID id);

    /** 
     * Validates if there's an active or inactive clown with that name, ignoring case. 
     * Useful for throwing 409 before saving. 
     */
    boolean existsByNameIgnoreCase(String name);

    /** Same as the previous one, excluding an id: used during updates. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** 
     * Finds active clowns and fetches their assigned cows in a single query.
     * Prevents the N+1 problem when returning a list of clowns with their cows.
     */
    @Query("""
            SELECT c FROM Clown c
            LEFT JOIN FETCH c.cows
            WHERE c.active = true
            ORDER BY c.name
            """)
    List<Clown> findAllActiveWithCows();

    /** 
     * Fetches an active clown and their cows. Same logic as above but for a single clown.
     * Returns Optional to throw a 404 easily if not found.
     */
    @Query("""
            SELECT c FROM Clown c
            LEFT JOIN FETCH c.cows
            WHERE c.id = :id AND c.active = true
            """)
    Optional<Clown> findActiveWithCowsById(@Param("id") UUID id);

    /**
     * Finds the clowns assigned to a specific cow.
     * The JOIN looks at the owner side (c.cows cow).
     */
    @Query("""
            SELECT c FROM Clown c
            JOIN c.cows cow
            WHERE cow.id = :cowId AND c.active = true
            ORDER BY c.name
            """)
    List<Clown> findActiveByCowId(@Param("cowId") UUID cowId);
}
