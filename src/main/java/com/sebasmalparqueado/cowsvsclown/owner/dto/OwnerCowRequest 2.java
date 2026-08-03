package com.sebasmalparqueado.cowsvsclown.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cow that is created <b>in the same request</b> as its owner.
 *
 * <p>It does not have ownerId: the owner is precisely the one being created. It is
 * the most direct way of 1 to N insertion, because the cascade = ALL of
 * {@code @OneToMany} saves owner and cows in a single transaction.</p>
 */
@Schema(description = "Cow created together with its owner in a single request")
public record OwnerCowRequest(

        @NotBlank(message = "cow name is mandatory")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        @Schema(description = "Cow name", example = "Lola")
        String name,

        @Min(value = 1, message = "weight must be greater than 0")
        @Schema(description = "Weight in kilograms", example = "450")
        int weight,

        @Min(value = 0, message = "milk production cannot be negative")
        @Schema(description = "Liters of milk per day", example = "12")
        int milkperday
) {
}
