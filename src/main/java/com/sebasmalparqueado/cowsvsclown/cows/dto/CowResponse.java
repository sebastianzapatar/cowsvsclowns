package com.sebasmalparqueado.cowsvsclown.cows.dto;

import java.util.List;
import java.util.UUID;

public record CowResponse(
        UUID id,
        String name,
        int weight,
        int milkperday,
        List<String> clownsname
) {
}
