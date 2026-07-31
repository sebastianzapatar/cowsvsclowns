package com.sebasmalparqueado.cowsvsclown.clowns.entity;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clown. It is the <b>owner</b> side of the N to M relationship with {@link Cow}: here
 * the {@code @JoinTable} is declared, so Hibernate only looks at this list to
 * decide which rows to insert or delete in the join table.
 *
 * <p>Practical consequence: to assign a cow to a clown you have to modify
 * {@code clown.getCows()}. Modifying {@code cow.getClowns()} doesn't save anything.</p>
 */
@Entity
@Table(name = "clowns")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Clown {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /** Logical delete: same idea as in the other entities. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Owner side of the N to M. The join table "clown_cow" only has the two
     * foreign keys, and the pair (clown_id, cow_id) is unique: this prevents
     * the same cow from being assigned twice to the same clown.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "clown_cow",
            joinColumns = @JoinColumn(name = "clown_id"),
            inverseJoinColumns = @JoinColumn(name = "cow_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_clown_cow",
                    columnNames = {"clown_id", "cow_id"}
            )
    )
    @Builder.Default
    private List<Cow> cows = new ArrayList<>();

    /**
     * Assigns a cow, synchronizing both sides in memory.
     *
     * <p>Persisting only depends on {@code cows}, but keeping
     * {@code cow.getClowns()} up to date also prevents an object already loaded
     * in the same transaction from returning an outdated list when mapping the response.</p>
     */
    public void addCow(Cow cow) {
        cows.add(cow);
        cow.getClowns().add(this);
    }

    /** Removes the assignment with that cow on both sides. */
    public void removeCow(Cow cow) {
        cows.remove(cow);
        cow.getClowns().remove(this);
    }

    /** True if this cow is already assigned to this clown. */
    public boolean hasCow(Cow cow) {
        return cows.stream().anyMatch(c -> c.getId().equals(cow.getId()));
    }
}
