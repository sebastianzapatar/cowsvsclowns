package com.sebasmalparqueado.cowsvsclown.cows.service;


import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.mapper.CowMapper;
import com.sebasmalparqueado.cowsvsclown.cows.repository.ICowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

}
