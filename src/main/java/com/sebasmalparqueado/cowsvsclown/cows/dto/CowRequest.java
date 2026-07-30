package com.sebasmalparqueado.cowsvsclown.cows.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CowRequest(
        @NotBlank(message = "name is mandatory")
        @Size(min=2,max=100,message = "between 2 and 100 characters")
        String name,
        @Min(1)
        int weight,
        @Min(0)
        int milkperday

) {
}
