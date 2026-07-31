package com.sebasmalparqueado.cowsvsclown.cows.controller;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de vacas.
 *
 * <p>El controller no tiene lógica: recibe la petición, se la pasa al servicio
 * y traduce el resultado a un código HTTP. Tampoco tiene try/catch: de los
 * errores se encarga {@code GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/cows")
@RequiredArgsConstructor
@Slf4j
@Validated // habilita las validaciones de los parámetros sueltos (@NotBlank de abajo)
@Tag(name = "Vacas", description = "CRUD de vacas y manejo de su dueño (relación 1 a N)")
public class CowController {

    private final CowService cowService;

    @GetMapping
    @Operation(
            summary = "Listar vacas",
            description = "Devuelve todas las vacas activas con su dueño y sus payasos. "
                    + "Las dadas de baja lógicamente no aparecen."
    )
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    public ResponseEntity<List<CowResponse>> getAllCows() {
        return ResponseEntity.ok(cowService.getCows());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar una vaca por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vaca encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe esa vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> getCowById(
            @Parameter(description = "Id de la vaca") @PathVariable UUID id) {

        return ResponseEntity.ok(cowService.getById(id));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Buscar una vaca por nombre exacto",
            description = "Usa la consulta en SQL nativo del repositorio. "
                    + "No distingue mayúsculas de minúsculas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vaca encontrada"),
            @ApiResponse(responseCode = "404", description = "No hay ninguna vaca con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> getCowByName(
            @Parameter(description = "Nombre exacto de la vaca", example = "Lola")
            @RequestParam @NotBlank(message = "el nombre a buscar no puede estar vacío")
            String name) {

        return ResponseEntity.ok(cowService.getByName(name));
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(
            summary = "Listar las vacas de un dueño",
            description = "Es el lado N de la relación 1 a N, consultado desde la vaca."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido"),
            @ApiResponse(responseCode = "404", description = "No existe ese dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<CowResponse>> getCowsByOwner(
            @Parameter(description = "Id del dueño") @PathVariable Long ownerId) {

        return ResponseEntity.ok(cowService.getByOwner(ownerId));
    }

    @PostMapping
    @Operation(
            summary = "Crear una vaca",
            description = """
                    Resuelve las dos relaciones de una sola vez:
                    - ownerId (obligatorio) crea el vínculo 1 a N con el dueño.
                    - clownIds (opcional) crea los vínculos N a M con los payasos.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vaca creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El dueño o algún payaso no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay una vaca con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> createCow(@Valid @RequestBody CowRequest request) {
        CowResponse created = cowService.create(request);

        // 201 + cabecera Location con la URL del recurso nuevo: es lo que
        // corresponde en REST para un POST que crea algo.
        return ResponseEntity
                .created(URI.create("/api/cows/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Modificar una vaca",
            description = "Solo cambia los campos que se envían; los que no vengan se dejan igual."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vaca actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o petición vacía",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe esa vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay otra vaca con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> updateCow(
            @Parameter(description = "Id de la vaca") @PathVariable UUID id,
            @Valid @RequestBody CowUpdateRequest request) {

        /*
         * Va 200 y no 206. El 206 (Partial Content) es para respuestas que
         * traen solo un pedazo del recurso, como una descarga por rangos; no
         * tiene nada que ver con que la actualización sea parcial.
         */
        return ResponseEntity.ok(cowService.update(id, request));
    }

    @PatchMapping("/{id}/owner/{ownerId}")
    @Operation(
            summary = "Cambiarle el dueño a una vaca",
            description = "Relación 1 a N: mueve la vaca de un dueño a otro "
                    + "actualizando la columna owner_id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dueño cambiado"),
            @ApiResponse(responseCode = "404", description = "No existe la vaca o el dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "La vaca ya era de ese dueño",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> changeOwner(
            @Parameter(description = "Id de la vaca") @PathVariable UUID id,
            @Parameter(description = "Id del nuevo dueño") @PathVariable Long ownerId) {

        return ResponseEntity.ok(cowService.changeOwner(id, ownerId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Dar de baja una vaca",
            description = "Baja lógica: la fila no se borra, se marca active = false "
                    + "y deja de aparecer en las consultas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vaca dada de baja"),
            @ApiResponse(responseCode = "404", description = "No existe esa vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteCow(
            @Parameter(description = "Id de la vaca") @PathVariable UUID id) {

        cowService.softDelete(id);
        // 204 No Content: la operación salió bien y no hay cuerpo que devolver.
        return ResponseEntity.noContent().build();
    }
}
