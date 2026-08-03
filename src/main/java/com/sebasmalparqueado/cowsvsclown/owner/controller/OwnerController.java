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
 * Owner endpoints. The owner is the "1" side of the 1 to N relationship with the
 * cows, so from here you can create an owner with all their cows at once.
 */
@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Owners", description = "Owner CRUD and " +
        "cascading creation of their cows (1 to N)")
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping
    @Operation(
            summary = "List owners",
            description = "Returns all active " +
                    "owners with their active cows."
    )
    @ApiResponse(responseCode = "200", description = "List obtained")
    public ResponseEntity<List<OwnerResponse>> getAllOwners() {
        return ResponseEntity.ok(ownerService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find an owner by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Owner found"),
            @ApiResponse(responseCode = "404",
                    description = "Owner does not exist",
                    content =
                    @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerResponse> getOwnerById(
            @Parameter(description = "Owner id") @PathVariable Long id) {

        return ResponseEntity.ok(ownerService.getById(id));
    }

    @PostMapping
    @Operation(
            summary = "Create an owner",
            description = """
                    If the request brings the "cows" list, those cows are created together
                    with the owner in the same transaction: this is the cascading 1 to N
                    insertion. If it is not provided, the owner is created alone and the cows are
                    added later with POST /api/cows.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Owner created"),
            @ApiResponse(responseCode = "400", description = "Invalid data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Owner already exists, or a cow repeats a name",
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
            summary = "Modify an owner",
            description = "Only changes the fields that are sent; those not provided are left unchanged."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data or empty request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Owner does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "There is already another owner with that name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerResponse> updateOwner(
            @Parameter(description = "Owner id") @PathVariable Long id,
            @Valid @RequestBody OwnerUpdateRequest request) {

        return ResponseEntity.ok(ownerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Logically delete an owner",
            description = "Logical delete. Their cows are also deactivated, because "
                    + "the owner_id column is mandatory and they cannot be left without an owner. "
                    + "To keep them they must be transferred beforehand with "
                    + "PATCH /api/cows/{id}/owner/{ownerId}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Owner logically deleted"),
            @ApiResponse(responseCode = "404", description = "Owner does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteOwner(
            @Parameter(description = "Owner id") @PathVariable Long id) {

        ownerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
