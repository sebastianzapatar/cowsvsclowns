package com.sebasmalparqueado.cowsvsclown.cows.entity;


import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.owner.entity.Owner;
import jakarta.persistence.*;
import lombok.*;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="cows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private int weight;
    private int milkperday;
    @ManyToMany(mappedBy = "cows",
            fetch=FetchType.LAZY)
    @Builder.Default
    List<Clown> clowns=new ArrayList<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="owner_id",nullable = false)
    Owner owner;

    
}
