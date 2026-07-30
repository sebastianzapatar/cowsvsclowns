package com.sebasmalparqueado.cowsvsclown.owner.entity;

import com.sebasmalparqueado.cowsvsclown.clowns.entity.Clown;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    @OneToMany(mappedBy = "owner"
    ,cascade = CascadeType.ALL,
    orphanRemoval = true,fetch = FetchType.LAZY)
    @Builder.Default
    private List<Cow> cows=new ArrayList<>();
}
