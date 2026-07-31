package com.sebasmalparqueado.cowsvsclown.clowns.entity;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payaso. Es el lado <b>dueño</b> de la relación N a M con {@link Cow}: acá se
 * declara la {@code @JoinTable}, así que Hibernate solo mira esta lista para
 * decidir qué filas insertar o borrar en la tabla intermedia.
 *
 * <p>Consecuencia práctica: para asignar una vaca a un payaso hay que tocar
 * {@code clown.getCows()}. Tocar {@code cow.getClowns()} no guarda nada.</p>
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

    /** Borrado lógico: misma idea que en las otras entidades. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Lado dueño del N a M. La tabla intermedia "clown_cow" solo tiene las dos
     * llaves foráneas, y el par (clown_id, cow_id) es único: eso evita que la
     * misma vaca quede asignada dos veces al mismo payaso.
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
     * Asigna una vaca sincronizando los dos lados en memoria.
     *
     * <p>Persistir solo depende de {@code cows}, pero mantener también
     * {@code cow.getClowns()} al día evita que un objeto ya cargado en la misma
     * transacción devuelva una lista desactualizada al mapear la respuesta.</p>
     */
    public void addCow(Cow cow) {
        cows.add(cow);
        cow.getClowns().add(this);
    }

    /** Quita la asignación con esa vaca en los dos lados. */
    public void removeCow(Cow cow) {
        cows.remove(cow);
        cow.getClowns().remove(this);
    }

    /** True si esta vaca ya está asignada a este payaso. */
    public boolean hasCow(Cow cow) {
        return cows.stream().anyMatch(c -> c.getId().equals(cow.getId()));
    }
}
