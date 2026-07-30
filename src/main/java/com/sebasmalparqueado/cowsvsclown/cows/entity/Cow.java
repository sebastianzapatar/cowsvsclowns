package com.sebasmalparqueado.cowsvsclown.cows.entity;


import jakarta.persistence.*;
import lombok.*;


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


    
}
