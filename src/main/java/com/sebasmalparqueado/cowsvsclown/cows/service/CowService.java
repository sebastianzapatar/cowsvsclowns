package com.sebasmalparqueado.cowsvsclown.cows.service;


import com.sebasmalparqueado.cowsvsclown.common.exceptions.ResourceNotFoundException;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.entity.Cow;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CowService {
    private final ICowRepository cowRepository;

    @Transactional(readOnly = true)
    public List<CowResponse> getCows() {
        return cowRepository.findAll()
                .stream()
                .map(CowMapper::cowMapper).toList();
    }
    //todo insertar, eliminar, editar, borrar
    //recuerden que nunca se eliminan registros de la bd
    @Transactional
    public CowResponse addCow(CowRequest cowRequest) {
        Cow cow = CowMapper.toEntity(cowRequest);
        Cow cowSaved = cowRepository.save(cow);
        return CowMapper.cowMapper(cowSaved);
    }
    @Transactional
    public CowResponse updateCow(UUID id,CowRequest cowRequest) {
        Cow existingCow = cowRepository.findById(id)
                        .orElseThrow(()->
                                new ResourceNotFoundException("Cow not found"));
        existingCow.setName(cowRequest.name());
        existingCow.setMilkperday(cowRequest.milkperday());
        existingCow.setWeight(cowRequest.weight());
        Cow cowUpdates=cowRepository.save(existingCow);
        return CowMapper.cowMapper(cowUpdates);
    }
}
