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
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersValues;
import org.gridsuite.shortcircuit.server.service.ShortCircuitParametersService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/" + ShortCircuitApi.API_VERSION + "/parameters", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Short circuit server analysis parameters")
public class ShortCircuitParametersController {
    public static final String DUPLICATE_FROM = "duplicateFrom";

    private final ShortCircuitParametersService shortCircuitParametersService;

    public ShortCircuitParametersController(ShortCircuitParametersService shortCircuitParametersService) {
        this.shortCircuitParametersService = shortCircuitParametersService;
    }

    @GetMapping(path = "/{parametersUuid}")
    @Operation(summary = "Get the parameters for an analysis")
    @ApiResponse(responseCode = "200", description = "The parameters asked")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<ShortCircuitParametersInfos> getParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid) {
        return ResponseEntity.of(shortCircuitParametersService.getParameters(parametersUuid));
    }

    @GetMapping(value = "/{parametersUuid}/values", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get parameters values")
    @ApiResponse(responseCode = "200", description = "parameters values were returned")
    @ApiResponse(responseCode = "404", description = "parameters were not found")
    public ResponseEntity<ShortCircuitParametersValues> getParametersValues(
            @Parameter(description = "parameters UUID") @PathVariable("parametersUuid") UUID parametersUuid,
            @Parameter(description = "provider name") @RequestParam("provider") String provider) {
        return ResponseEntity.of(shortCircuitParametersService.getParametersValues(parametersUuid, provider));
    }

    @GetMapping(value = "/specific-parameters")
    @Operation(summary = "Get all existing shortcircuit specific parameters for a given provider, or for all of them")
    @ApiResponse(responseCode = "200", description = "The shortcircuit model-specific parameters")
    public ResponseEntity<Map<String, List<com.powsybl.commons.parameters.Parameter>>> getSpecificShortCircuitParameters(
            @Parameter(description = "The model provider") @RequestParam(name = "provider", required = false) String provider) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(ShortCircuitParametersService.getSpecificShortCircuitParameters(provider));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new set of parameters for an analysis using given parameters")
    @ApiResponse(responseCode = "200", description = "The new parameters entity ID")
    public ResponseEntity<UUID> createParameters(@Parameter(description = "Parameters to save") @RequestBody ShortCircuitParametersInfos parameters) {
        return ResponseEntity.ok(shortCircuitParametersService.createParameters(parameters));
    }

    @PostMapping(path = "/default")
    @Operation(summary = "Create a new set of parameters for an analysis using default parameters")
    @ApiResponse(responseCode = "200", description = "The new parameters entity ID")
    public ResponseEntity<UUID> createDefaultParameters() {
        return ResponseEntity.ok(shortCircuitParametersService.createDefaultParameters());
    }

    @PostMapping(params = { DUPLICATE_FROM })
    @Operation(summary = "Duplicate the parameters of an analysis")
    @ApiResponse(responseCode = "200", description = "The new parameters ID")
    @ApiResponse(responseCode = "404", description = "The parameters don't exist")
    public ResponseEntity<UUID> duplicateParameters(@Parameter(description = "UUID of parameters to duplicate") @RequestParam(name = DUPLICATE_FROM) UUID sourceParametersUuid) {
        return ResponseEntity.of(shortCircuitParametersService.duplicateParameters(sourceParametersUuid));
    }

    @DeleteMapping(path = "/{parametersUuid}")
    @Operation(summary = "Delete a set of parameters")
    @ApiResponse(responseCode = "200", description = "The parameters are successfully deleted")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<Void> deleteParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid) {
        return (shortCircuitParametersService.deleteParameters(parametersUuid) ? ResponseEntity.ok() : ResponseEntity.notFound()).build();
    }

    @PutMapping(path = "/{parametersUuid}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Update parameters for an analysis or reset them to default ones")
    @ApiResponse(responseCode = "200", description = "The parameters are successfully updated")
    @ApiResponse(responseCode = "404", description = "The parameters don't exists")
    public ResponseEntity<Void> updateOrResetParameters(@Parameter(description = "UUID of parameters") @PathVariable("parametersUuid") UUID parametersUuid,
                                                        @Parameter(description = "Parameters to save instead of default ones", schema = @Schema(implementation = ShortCircuitParametersInfos.class))
                                                        @RequestBody(required = false) ShortCircuitParametersInfos parameters) {
        try {
            shortCircuitParametersService.updateParameters(parametersUuid, parameters);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/{uuid}/provider")
    @Operation(summary = "Get the provider")
    @ApiResponse(responseCode = "200", description = "provider were returned")
    public ResponseEntity<String> getProvider(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        return ResponseEntity.ok().body(shortCircuitParametersService.getProvider(parametersUuid));
    }
}
