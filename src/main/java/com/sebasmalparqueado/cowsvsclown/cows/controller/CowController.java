package com.sebasmalparqueado.cowsvsclown.cows.controller;

import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cows")
@RequiredArgsConstructor
@Slf4j//para los logs
public class CowController {
    private final CowService cowService;

    @GetMapping
    public ResponseEntity<List<CowResponse>> getAllCows() {
        List<CowResponse> cowcitas=cowService.getCows();
        return ResponseEntity.ok(cowcitas);
    }

}
