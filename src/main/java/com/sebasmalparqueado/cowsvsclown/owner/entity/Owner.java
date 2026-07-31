package com.sebasmalparqueado.cowsvsclown.owner.entity;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Cow owner. It is the "1" side of the 1 to N relationship with {@link Cow}.
 *
 * <p>An owner has many cows; a cow belongs to a single owner.
 * The foreign key (owner_id) does NOT live here but in the "cows" table: in JPA the FK
 * always stays on the {@code @ManyToOne} side.</p>
 */
@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    /**
     * Logical delete. A record is never deleted from the database: it is marked as
     * false and stops appearing in queries. This preserves the history
     * and leaves no cows pointing to an owner that no longer exists.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Inverse side of the 1 to N: {@code mappedBy = "owner"} means that the
     * field {@code owner} in Cow rules the relationship.
     *
     * <p>cascade = ALL + orphanRemoval make it so that when an owner is saved,
     * its new cows are saved, and when a cow is removed from this list, it is deleted from the
     * database. LAZY avoids fetching all cows every time an owner is read.</p>
     */
    @OneToMany(
            mappedBy = "owner",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Cow> cows = new ArrayList<>();

    /**
     * Adds a cow synchronizing both sides of the relationship.
     *
     * <p>It is the key point of the 1 to N insertion: if only
     * {@code owner.getCows().add(cow)} was done without assigning {@code cow.setOwner(this)},
     * Hibernate would try to save the cow with owner_id in null and fail due to
     * the column's NOT NULL constraint.</p>
     */
    public void addCow(Cow cow) {
        cows.add(cow);
        cow.setOwner(this);
    }

    /** Removes a cow from this owner and breaks the inverse reference. */
    public void removeCow(Cow cow) {
        cows.remove(cow);
        cow.setOwner(null);
    }

    /** Full name, used by response DTOs. */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
