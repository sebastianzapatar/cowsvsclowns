package com.sebasmalparqueado.cowsvsclown.owner.entity;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Dueño de vacas. Es el lado "1" de la relación 1 a N con {@link Cow}.
 *
 * <p>Un dueño tiene muchas vacas; una vaca pertenece a un solo dueño.
 * La llave foránea (owner_id) NO vive acá sino en la tabla "cows": en JPA la FK
 * siempre queda del lado del {@code @ManyToOne}.</p>
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
     * Borrado lógico. En la base nunca se elimina un registro: se marca en
     * false y deja de aparecer en las consultas. Así se conserva el histórico
     * y no quedan vacas apuntando a un dueño que ya no existe.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Lado inverso del 1 a N: {@code mappedBy = "owner"} significa que quien
     * manda en la relación es el campo {@code owner} de Cow.
     *
     * <p>cascade = ALL + orphanRemoval hacen que al guardar un dueño se guarden
     * sus vacas nuevas, y que al sacar una vaca de esta lista se borre de la
     * base. LAZY evita traer todas las vacas cada vez que se lee un dueño.</p>
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
     * Agrega una vaca sincronizando los dos lados de la relación.
     *
     * <p>Es el punto clave de la inserción 1 a N: si solo se hiciera
     * {@code owner.getCows().add(cow)} sin asignar {@code cow.setOwner(this)},
     * Hibernate intentaría guardar la vaca con owner_id en null y fallaría por
     * la restricción NOT NULL de la columna.</p>
     */
    public void addCow(Cow cow) {
        cows.add(cow);
        cow.setOwner(this);
    }

    /** Quita una vaca de este dueño y corta la referencia inversa. */
    public void removeCow(Cow cow) {
        cows.remove(cow);
        cow.setOwner(null);
    }

    /** Nombre completo, usado por los DTO de respuesta. */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
