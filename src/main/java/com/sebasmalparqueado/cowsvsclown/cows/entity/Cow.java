package com.sebasmalparqueado.cowsvsclown.cows.entity;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cow. Participates in both model relationships:
 *
 * <ul>
 *   <li><b>N to 1</b> with {@link Owner}: here lives the owner_id foreign key.</li>
 *   <li><b>N to M</b> with {@link Clown}: resolved by the join table
 *       "clown_cow". This entity is the <i>inverse</i> side (mappedBy).</li>
 * </ul>
 */
@Entity
@Table(name = "cows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Weight in kilograms. */
    @Column(nullable = false)
    private int weight;

    /** Liters of milk produced per day. */
    @Column(nullable = false)
    private int milkperday;

    /** Logical delete: same idea as in {@link Owner}. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Inverse side of the N to M. {@code mappedBy = "cows"} means that the join
     * table is managed by {@link Clown}; changes made to this list
     * ARE NOT persisted. That's why cow-clown assignments are always done
     * from the clown (see {@code ClownService}).
     */
    @ManyToMany(mappedBy = "cows", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Clown> clowns = new ArrayList<>();

    /**
     * Owner side of the N to 1: the owner_id column is created in this table.
     * It is mandatory, so every cow must be born with a valid owner.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;
}
