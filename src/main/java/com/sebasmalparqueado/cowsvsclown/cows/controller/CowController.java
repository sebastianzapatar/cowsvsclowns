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
 * Cow endpoints.
 *
 * <p>The controller has no logic: it receives the request, passes it to the service
 * and translates the result into an HTTP status code. It also has no try/catch:
 * errors are handled by {@code GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/cows")
@RequiredArgsConstructor
@Slf4j
@Validated // enables validation on independent parameters (e.g. @NotBlank below)
@Tag(name = "Cows", description = "Cow CRUD and owner management (1 to N relationship)")
public class CowController {

    private final CowService cowService;

    @GetMapping
    @Operation(
            summary = "List cows",
            description = "Returns all active cows with their owner and clowns. "
                    + "Logically deleted ones do not appear."
    )
    @ApiResponse(responseCode = "200", description = "List obtained")
    public ResponseEntity<List<CowResponse>> getAllCows() {
        return ResponseEntity.ok(cowService.getCows());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a cow by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cow found"),
            @ApiResponse(responseCode = "404", description = "Cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> getCowById(
            @Parameter(description = "Cow id") @PathVariable UUID id) {

        return ResponseEntity.ok(cowService.getById(id));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Find a cow by exact name",
            description = "Uses the native SQL query from the repository. "
                    + "Case-insensitive."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cow found"),
            @ApiResponse(responseCode = "404", description = "There is no cow with that name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> getCowByName(
            @Parameter(description = "Exact name of the cow", example = "Lola")
            @RequestParam @NotBlank(message = "the search name cannot be empty")
            String name) {

        return ResponseEntity.ok(cowService.getByName(name));
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(
            summary = "List cows of an owner",
            description = "This is the N side of the 1 to N relationship, queried from the cow."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List obtained"),
            @ApiResponse(responseCode = "404", description = "Owner does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<CowResponse>> getCowsByOwner(
            @Parameter(description = "Owner id") @PathVariable Long ownerId) {

        return ResponseEntity.ok(cowService.getByOwner(ownerId));
    }

    @PostMapping
    @Operation(
            summary = "Create a cow",
            description = """
                    Resolves both relationships at once:
                    - ownerId (mandatory) creates the 1 to N link with the owner.
                    - clownIds (optional) creates the N to M links with the clowns.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cow created"),
            @ApiResponse(responseCode = "400", description = "Invalid data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Owner or some clown does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "There is already a cow with that name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> createCow(@Valid @RequestBody CowRequest request) {
        CowResponse created = cowService.create(request);

        // 201 + Location header with the URL of the new resource: this is
        // the proper REST way for a POST that creates something.
        return ResponseEntity
                .created(URI.create("/api/cows/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Modify a cow",
            description = "Only changes the fields that are sent; those not provided are left unchanged."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cow updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data or empty request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "There is already another cow with that name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> updateCow(
            @Parameter(description = "Cow id") @PathVariable UUID id,
            @Valid @RequestBody CowUpdateRequest request) {

        /*
         * It's 200 and not 206. 206 (Partial Content) is for responses that
         * bring only a piece of the resource, like a range download; it has
         * nothing to do with a partial update.
         */
        return ResponseEntity.ok(cowService.update(id, request));
    }

    @PatchMapping("/{id}/owner/{ownerId}")
    @Operation(
            summary = "Change the owner of a cow",
            description = "1 to N relationship: moves the cow from one owner to another "
                    + "by updating the owner_id column."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner changed"),
            @ApiResponse(responseCode = "404", description = "Cow or owner does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The cow already belonged to that owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CowResponse> changeOwner(
            @Parameter(description = "Cow id") @PathVariable UUID id,
            @Parameter(description = "New owner id") @PathVariable Long ownerId) {

        return ResponseEntity.ok(cowService.changeOwner(id, ownerId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Logically delete a cow",
            description = "Logical delete: the row is not deleted, it is marked active = false "
                    + "and stops appearing in queries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cow deleted"),
            @ApiResponse(responseCode = "404", description = "Cow does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteCow(
            @Parameter(description = "Cow id") @PathVariable UUID id) {

        cowService.softDelete(id);
        // 204 No Content: the operation succeeded and there is no body to return.
        return ResponseEntity.noContent().build();
    }
}
