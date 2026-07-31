package com.sebasmalparqueado.cowsvsclown.owner.repository;

import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de {@link Owner}.
 *
 * <p>Al extender {@code JpaRepository} ya vienen gratis save, findAll, count,
 * etc. Los métodos de acá se resuelven solos por el nombre (Spring Data arma la
 * consulta leyendo "findAllByActiveTrue..."), salvo los que llevan
 * {@code @Query}, que se escriben a mano para poder usar JOIN FETCH.</p>
 */
public interface IOwnerRepository extends JpaRepository<Owner, Long> {

    /**
     * Un dueño activo por id. Es el findById que se usa en toda la aplicación:
     * el de JpaRepository también devolvería los dados de baja lógicamente.
     */
    Optional<Owner> findByIdAndActiveTrue(Long id);

    /** ¿Ya hay un dueño activo con ese nombre y apellido? Sirve para no duplicar. */
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrue(
            String firstName, String lastName);

    /**
     * Igual que el anterior pero excluyendo un id.
     * Se usa al actualizar: sin el "AndIdNot", un dueño chocaría consigo mismo
     * al guardarlo sin cambiarle el nombre.
     */
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndActiveTrueAndIdNot(
            String firstName, String lastName, Long id);

    /**
     * Todos los dueños activos con sus vacas ya cargadas.
     *
     * <p>El LEFT JOIN FETCH trae dueños y vacas en UNA sola consulta. Sin eso,
     * al ser la relación LAZY, el mapper dispararía una consulta por cada dueño
     * al pedirle las vacas: el problema N+1.</p>
     *
     * <p>Es LEFT y no INNER para que también salgan los dueños sin vacas.
     * No hace falta DISTINCT: Hibernate 6 ya elimina los duplicados que genera
     * el join al construir las entidades.</p>
     */
    @Query("""
            SELECT o FROM Owner o
            LEFT JOIN FETCH o.cows
            WHERE o.active = true
            ORDER BY o.lastName, o.firstName
            """)
    List<Owner> findAllActiveWithCows();

    /** Un dueño activo con sus vacas ya cargadas, en una sola consulta. */
    @Query("""
            SELECT o FROM Owner o
            LEFT JOIN FETCH o.cows
            WHERE o.id = :id AND o.active = true
            """)
    Optional<Owner> findActiveWithCowsById(@Param("id") Long id);
}
