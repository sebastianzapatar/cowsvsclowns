package com.sebasmalparqueado.cowsvsclown.cows.repository;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ICowRepository extends
        JpaRepository<Cow, UUID> {
    //INSERTAR ELIMINAR ACTUALIZAR BUSCAR POR ID
    Optional<Cow> findByName(String name);

    Optional<Cow> findById(UUID id);

    @Query(value = "SELECT c FROM cows " +
            "WHERE name=:name LIMIT 1",
    nativeQuery = true)
    Optional<Cow> findByNameSQL(@Param("name") String name);
}
