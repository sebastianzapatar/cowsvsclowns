package com.sebasmalparqueado.cowsvsclown.owner.controller;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.owner.service.OwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de dueños. El dueño es el lado "1" de la relación 1 a N con las
 * vacas, así que desde acá se puede crear un dueño con todas sus vacas de una.
 */
@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dueños", description = "CRUD de dueños y creación en cascada de sus vacas (1 a N)")
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping
    @Operation(
            summary = "Listar dueños",
            description = "Devuelve todos los dueños activos con sus vacas activas."
    )
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    public ResponseEntity<List<OwnerResponse>> getAllOwners() {
        return ResponseEntity.ok(ownerService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar un dueño por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dueño encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe ese dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerResponse> getOwnerById(
            @Parameter(description = "Id del dueño") @PathVariable Long id) {

        return ResponseEntity.ok(ownerService.getById(id));
    }

    @PostMapping
    @Operation(
            summary = "Crear un dueño",
            description = """
                    Si la petición trae la lista "cows", esas vacas se crean junto
                    con el dueño en la misma transacción: es la inserción 1 a N en
                    cascada. Si no viene, el dueño se crea solo y las vacas se le
                    agregan después con POST /api/cows.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dueño creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Ya existe ese dueño, o alguna vaca repite un nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerResponse> createOwner(@Valid @RequestBody OwnerRequest request) {
        OwnerResponse created = ownerService.create(request);

        return ResponseEntity
                .created(URI.create("/api/owners/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Modificar un dueño",
            description = "Solo cambia los campos que se envían; los que no vengan se dejan igual."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dueño actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o petición vacía",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay otro dueño con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerResponse> updateOwner(
            @Parameter(description = "Id del dueño") @PathVariable Long id,
            @Valid @RequestBody OwnerUpdateRequest request) {

        return ResponseEntity.ok(ownerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Dar de baja un dueño",
            description = "Baja lógica. Sus vacas también quedan inactivas, porque "
                    + "la columna owner_id es obligatoria y no pueden quedar sin dueño. "
                    + "Para conservarlas hay que traspasarlas antes con "
                    + "PATCH /api/cows/{id}/owner/{ownerId}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dueño dado de baja"),
            @ApiResponse(responseCode = "404", description = "No existe ese dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteOwner(
            @Parameter(description = "Id del dueño") @PathVariable Long id) {

        ownerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
