package com.sebasmalparqueado.cowsvsclown.clowns.controller;

import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownResponse;
import com.sebasmalparqueado.cowsvsclown.clowns.dto.ClownUpdateRequest;
import com.sebasmalparqueado.cowsvsclown.clowns.service.ClownService;
import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
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
import java.util.UUID;

/**
 * Endpoints de payasos.
 *
 * <p>Acá viven las operaciones de la relación N a M, porque {@code Clown} es el
 * lado dueño: los sub-recursos {@code /api/clowns/{id}/cows/{cowId}} son los que
 * crean y borran filas en la tabla intermedia clown_cow.</p>
 */
@RestController
@RequestMapping("/api/clowns")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payasos", description = "CRUD de payasos y asignación de vacas (relación N a M)")
public class ClownController {

    private final ClownService clownService;

    @GetMapping
    @Operation(
            summary = "Listar payasos",
            description = "Devuelve todos los payasos activos con las vacas que tienen asignadas."
    )
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    public ResponseEntity<List<ClownResponse>> getAllClowns() {
        return ResponseEntity.ok(clownService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar un payaso por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payaso encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe ese payaso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> getClownById(
            @Parameter(description = "Id del payaso") @PathVariable UUID id) {

        return ResponseEntity.ok(clownService.getById(id));
    }

    @GetMapping("/cow/{cowId}")
    @Operation(
            summary = "Listar los payasos que cuidan una vaca",
            description = "Es la relación N a M leída desde el lado de la vaca."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido"),
            @ApiResponse(responseCode = "404", description = "No existe esa vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ClownResponse>> getClownsByCow(
            @Parameter(description = "Id de la vaca") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.getByCow(cowId));
    }

    @PostMapping
    @Operation(
            summary = "Crear un payaso",
            description = """
                    Si la petición trae la lista "cowIds", esas vacas quedan
                    asignadas al payaso en la misma transacción: por cada id se
                    inserta una fila en la tabla intermedia clown_cow.
                    Las vacas tienen que existir y estar activas.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payaso creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Alguna de las vacas no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay un payaso con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> createClown(@Valid @RequestBody ClownRequest request) {
        ClownResponse created = clownService.create(request);

        return ResponseEntity
                .created(URI.create("/api/clowns/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Modificar un payaso",
            description = "Solo cambia los campos que se envían; los que no vengan se dejan igual."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payaso actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o petición vacía",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese payaso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay otro payaso con ese nombre",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> updateClown(
            @Parameter(description = "Id del payaso") @PathVariable UUID id,
            @Valid @RequestBody ClownUpdateRequest request) {

        return ResponseEntity.ok(clownService.update(id, request));
    }

    @PostMapping("/{clownId}/cows/{cowId}")
    @Operation(
            summary = "Asignar una vaca a un payaso",
            description = "Inserción N a M: crea la fila (clown_id, cow_id) en la "
                    + "tabla intermedia clown_cow. Las dos entidades tienen que existir."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vaca asignada"),
            @ApiResponse(responseCode = "404", description = "No existe el payaso o la vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Esa vaca ya estaba asignada a ese payaso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> assignCow(
            @Parameter(description = "Id del payaso") @PathVariable UUID clownId,
            @Parameter(description = "Id de la vaca") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.assignCow(clownId, cowId));
    }

    @DeleteMapping("/{clownId}/cows/{cowId}")
    @Operation(
            summary = "Quitarle una vaca a un payaso",
            description = "Borra la fila de la tabla intermedia. Acá el borrado sí es "
                    + "físico: la tabla clown_cow solo representa el vínculo, no guarda "
                    + "datos propios. Ni la vaca ni el payaso se tocan."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vaca desasignada"),
            @ApiResponse(responseCode = "404", description = "No existe el payaso o la vaca",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Esa vaca no estaba asignada a ese payaso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> unassignCow(
            @Parameter(description = "Id del payaso") @PathVariable UUID clownId,
            @Parameter(description = "Id de la vaca") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.unassignCow(clownId, cowId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Dar de baja un payaso",
            description = "Baja lógica: la fila no se borra, se marca active = false. "
                    + "Sus asignaciones en clown_cow se conservan como histórico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Payaso dado de baja"),
            @ApiResponse(responseCode = "404", description = "No existe ese payaso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteClown(
            @Parameter(description = "Id del payaso") @PathVariable UUID id) {

        clownService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
