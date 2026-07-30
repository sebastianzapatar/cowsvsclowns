package com.sebasmalparqueado.cowsvsclown.cows.mapper;

import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;

import java.util.List;

public class CowMapper {
    private CowMapper() {}

    public static Cow toEntity(CowRequest request) {
        if(request == null) return null;
        return Cow.
                builder().
                name(request.name())
                .weight(request.weight())
                .milkperday(request.milkperday())
                .build();

    }
    public static CowResponse cowMapper(Cow cow) {
        if(cow == null) return null;
        List<String> clowns=List.of("Daniel F",
                "Sebas","Isaac");
        return new CowResponse(
                cow.getId(),
                cow.getName(),
                cow.getWeight(),
                cow.getMilkperday(),
                clowns
        );
    }
}
