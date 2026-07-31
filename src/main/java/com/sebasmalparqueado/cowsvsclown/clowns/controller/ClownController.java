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
 * Clown endpoints.
 *
 * <p>Here live the operations for the N to M relationship, because {@code Clown} is the
 * owner side: the sub-resources {@code /api/clowns/{id}/cows/{cowId}} are the ones
 * that create and delete rows in the join table clown_cow.</p>
 */
@RestController
@RequestMapping("/api/clowns")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Clowns", description = "Clown CRUD and cow assignment (N to M relationship)")
public class ClownController {

    private final ClownService clownService;

    @GetMapping
    @Operation(
            summary = "List clowns",
            description = "Returns all active clowns with their assigned cows."
    )
    @ApiResponse(responseCode = "200", description = "List obtained")
    public ResponseEntity<List<ClownResponse>> getAllClowns() {
        return ResponseEntity.ok(clownService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a clown by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clown found"),
            @ApiResponse(responseCode = "404", description = "Clown does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> getClownById(
            @Parameter(description = "Clown id") @PathVariable UUID id) {

        return ResponseEntity.ok(clownService.getById(id));
    }

    @GetMapping("/cow/{cowId}")
    @Operation(
            summary = "List clowns that take care of a cow",
            description = "This is the N to M relationship read from the cow's side."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List obtained"),
            @ApiResponse(responseCode = "404", description = "Cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ClownResponse>> getClownsByCow(
            @Parameter(description = "Cow id") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.getByCow(cowId));
    }

    @PostMapping
    @Operation(
            summary = "Create a clown",
            description = """
                    If the request includes the "cowIds" list, those cows are
                    assigned to the clown in the same transaction: for each id a
                    row is inserted in the clown_cow join table.
                    Cows must exist and be active.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Clown created"),
            @ApiResponse(responseCode = "400", description = "Invalid data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "One of the cows does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "There is already a clown with that name",
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
            summary = "Modify a clown",
            description = "Only changes the fields that are sent; those not provided are left unchanged."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clown updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data or empty request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Clown does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "There is already another clown with that name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> updateClown(
            @Parameter(description = "Clown id") @PathVariable UUID id,
            @Valid @RequestBody ClownUpdateRequest request) {

        return ResponseEntity.ok(clownService.update(id, request));
    }

    @PostMapping("/{clownId}/cows/{cowId}")
    @Operation(
            summary = "Assign a cow to a clown",
            description = "N to M insertion: creates the row (clown_id, cow_id) in the "
                    + "clown_cow join table. Both entities must exist."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cow assigned"),
            @ApiResponse(responseCode = "404", description = "Clown or cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "That cow was already assigned to that clown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> assignCow(
            @Parameter(description = "Clown id") @PathVariable UUID clownId,
            @Parameter(description = "Cow id") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.assignCow(clownId, cowId));
    }

    @DeleteMapping("/{clownId}/cows/{cowId}")
    @Operation(
            summary = "Unassign a cow from a clown",
            description = "Deletes the row in the join table. This is a physical "
                    + "deletion: the clown_cow table only represents the link, it doesn't store "
                    + "its own data. Neither the cow nor the clown is touched."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cow unassigned"),
            @ApiResponse(responseCode = "404", description = "Clown or cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "That cow was not assigned to that clown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClownResponse> unassignCow(
            @Parameter(description = "Clown id") @PathVariable UUID clownId,
            @Parameter(description = "Cow id") @PathVariable UUID cowId) {

        return ResponseEntity.ok(clownService.unassignCow(clownId, cowId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Logically delete a clown",
            description = "Logical delete: the row is not deleted, it is marked active = false. "
                    + "Its assignments in clown_cow are preserved as history."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Clown deleted"),
            @ApiResponse(responseCode = "404", description = "Clown does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteClown(
            @Parameter(description = "Clown id") @PathVariable UUID id) {

        clownService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
