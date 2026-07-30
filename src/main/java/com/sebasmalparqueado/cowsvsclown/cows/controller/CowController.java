package com.sebasmalparqueado.cowsvsclown.cows.controller;

import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
    @PostMapping
    public ResponseEntity<CowResponse> createCow(@Valid  @RequestBody CowRequest request) {
        CowResponse w=cowService.addCow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(w);
    }
    @PatchMapping("/{id}")
    public ResponseEntity<CowResponse> updateCow(
            @PathVariable UUID id,
            @Valid  @RequestBody CowRequest request) {
        CowResponse w=cowService.updateCow(id, request);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).
                body(w);
    }

}
