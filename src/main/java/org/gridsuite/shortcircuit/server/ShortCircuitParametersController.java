/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/" + ShortCircuitApi.API_VERSION + "/parameters", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Short circuit server analysis parameters")
public class ShortCircuitParametersController {
    public static final String DUPLICATE_FROM = "duplicateFrom";

    private final ShortCircuitService shortCircuitService;

    public ShortCircuitParametersController(ShortCircuitService shortCircuitService) {
        this.shortCircuitService = shortCircuitService;
    }

    @GetMapping(path = "/{parametersUuid}")
    @Operation(summary = "Get the parameters for an analysis")
    @ApiResponse(responseCode = "200", description = "The parameters asked")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<ShortCircuitParametersInfos> getParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid) {
        return ResponseEntity.of(shortCircuitService.getParameters(parametersUuid));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new set of parameters for an analysis using given parameters")
    @ApiResponse(responseCode = "200", description = "The new parameters entity ID")
    public ResponseEntity<UUID> createParameters(@Parameter(description = "Parameters to save") @RequestBody ShortCircuitParametersInfos parameters) {
        return ResponseEntity.ok(shortCircuitService.createParameters(parameters));
    }

    @PostMapping(path = "/default")
    @Operation(summary = "Create a new set of parameters for an analysis using default parameters")
    @ApiResponse(responseCode = "200", description = "The new parameters entity ID")
    public ResponseEntity<UUID> createDefaultParameters() {
        return ResponseEntity.ok(shortCircuitService.createParameters(null));
    }

    @PostMapping(params = { DUPLICATE_FROM })
    @Operation(summary = "Duplicate the parameters of an analysis")
    @ApiResponse(responseCode = "200", description = "The new parameters ID")
    @ApiResponse(responseCode = "404", description = "The parameters don't exist")
    public ResponseEntity<UUID> duplicateParameters(@Parameter(description = "UUID of parameters to duplicate") @RequestParam(name = DUPLICATE_FROM) UUID sourceParametersUuid) {
        return ResponseEntity.of(shortCircuitService.duplicateParameters(sourceParametersUuid));
    }

    @DeleteMapping(path = "/{parametersUuid}")
    @Operation(summary = "Delete a set of parameters")
    @ApiResponse(responseCode = "204", description = "The parameters are successfully deleted")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<Void> deleteParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid) {
        return (shortCircuitService.deleteParameters(parametersUuid) ? ResponseEntity.noContent() : ResponseEntity.notFound()).build();
    }

    @PutMapping(path = "/{parametersUuid}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Update parameters for an analysis or reset them to default ones")
    @ApiResponse(responseCode = "204", description = "The parameters are successfully updated")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<Void> updateOrResetParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid,
                                                        @Parameter(description = "Parameters to save instead of default ones", schema = @Schema(implementation = ShortCircuitParametersInfos.class))
                                                        @RequestBody(required = false) ShortCircuitParametersInfos parameters) {
        return (shortCircuitService.updateOrResetParameters(parametersUuid, parameters) ? ResponseEntity.noContent() : ResponseEntity.notFound()).build();
    }
}
