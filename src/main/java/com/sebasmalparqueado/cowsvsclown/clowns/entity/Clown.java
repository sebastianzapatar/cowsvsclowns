package com.sebasmalparqueado.cowsvsclown.clowns.entity;

import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="clowns")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Clown {
    @Id
    @GeneratedValue(strategy= GenerationType.UUID)
    private UUID id;
    private String name;
    private String description;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name="clow_cow",
            joinColumns =
            @JoinColumn(name="clown_id"),
            inverseJoinColumns =
            @JoinColumn(name ="cow_id" )
    )
    @Builder.Default
    private List<Cow> cows=new ArrayList<>();
}
