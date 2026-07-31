package com.sebasmalparqueado.cowsvsclown.cows.entity;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vaca. Participa en las dos relaciones del modelo:
 *
 * <ul>
 *   <li><b>N a 1</b> con {@link Owner}: acá vive la llave foránea owner_id.</li>
 *   <li><b>N a M</b> con {@link Clown}: se resuelve con la tabla intermedia
 *       "clown_cow". Esta entidad es el lado <i>inverso</i> (mappedBy).</li>
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

    /** Peso en kilogramos. */
    @Column(nullable = false)
    private int weight;

    /** Litros de leche que produce por día. */
    @Column(nullable = false)
    private int milkperday;

    /** Borrado lógico: misma idea que en {@link Owner}. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Lado inverso del N a M. {@code mappedBy = "cows"} dice que la tabla
     * intermedia la administra {@link Clown}; los cambios hechos a esta lista
     * NO se persisten. Por eso las asignaciones vaca-payaso se hacen siempre
     * desde el payaso (ver {@code ClownService}).
     */
    @ManyToMany(mappedBy = "cows", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Clown> clowns = new ArrayList<>();

    /**
     * Lado dueño del N a 1: la columna owner_id se crea en esta tabla.
     * Es obligatoria, así que toda vaca tiene que nacer con un dueño válido.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;
}
