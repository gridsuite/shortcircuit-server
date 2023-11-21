/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.powsybl.shortcircuit.Fault.FaultType;
import static org.gridsuite.shortcircuit.server.service.NotificationService.HEADER_USER_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + ShortCircuitApi.API_VERSION)
@Tag(name = "Short circuit server")
public class ShortCircuitController {
    private final ShortCircuitService shortCircuitService;

    public ShortCircuitController(ShortCircuitService shortCircuitService) {
        this.shortCircuitService = shortCircuitService;
    }

    private static ShortCircuitParameters getNonNullParameters(ShortCircuitParameters parameters) {
        return parameters != null ? parameters : new ShortCircuitParameters();
    }

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a short circuit analysis on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
                                        description = "The short circuit analysis has been performed",
                                        content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = ShortCircuitParameters.class))})})
    public ResponseEntity<UUID> runAndSave(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                           @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "ShortCircuitAnalysis") String reportType,
                                           @Parameter(description = "Bus Id - Used for analysis targeting one bus") @RequestParam(name = "busId", required = false) String busId,
                                           @RequestBody(required = false) ShortCircuitParameters parameters,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        ShortCircuitParameters nonNullParameters = getNonNullParameters(parameters);
        UUID resultUuid = shortCircuitService.runAndSaveResult(new ShortCircuitRunContext(networkUuid, variantId, receiver, nonNullParameters, reportUuid, reporterId, reportType, userId, busId));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a short circuit analysis result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
        @ApiResponse(responseCode = "404", description = "Short circuit analysis result has not been found")})
    public ResponseEntity<ShortCircuitAnalysisResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                                @Parameter(description = "BASIC (faults without limits and feeders), " +
                                                                    "FULL (faults with both), " +
                                                                    "WITH_LIMIT_VIOLATIONS (like FULL but only those with limit violations) or " +
                                                                    "NONE (no fault)") @RequestParam(name = "mode", required = false, defaultValue = "WITH_LIMIT_VIOLATIONS") FaultResultsMode mode) {
        ShortCircuitAnalysisResult result = shortCircuitService.getResult(resultUuid, mode);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/results/{resultUuid}/fault_results/paged", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a fault results page for a given short circuit analysis result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The page of fault results"),
        @ApiResponse(responseCode = "404", description = "Short circuit analysis result has not been found")})
    public ResponseEntity<Page<FaultResult>> getPagedFaultResults(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                                  @Parameter(description = "BASIC (faults without limits and feeders), " +
                                                                      "FULL (faults with both), " +
                                                                      "WITH_LIMIT_VIOLATIONS (like FULL but only those with limit violations) or " +
                                                                      "NONE (no fault)") @RequestParam(name = "mode", required = false, defaultValue = "FULL") FaultResultsMode mode,
                                                                  @Parameter(description = "Filters") @RequestParam(name = "filters", required = false) String stringFilters,
                                                                  Pageable pageable) throws JsonProcessingException {
        List<ResourceFilter> resourceFilters = ResourceFilter.fromStringToList(stringFilters);
        Page<FaultResult> faultResultsPage = shortCircuitService.getFaultResultsPage(resultUuid, mode, resourceFilters, pageable);
        return faultResultsPage != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(faultResultsPage)
            : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/results/{resultUuid}/feeder_results/paged", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a feeder results page for a given short circuit analysis result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The page of feeder results"),
        @ApiResponse(responseCode = "404", description = "Short circuit analysis result has not been found")})
    public ResponseEntity<Page<FeederResult>> getPagedFeederResults(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                                    @Parameter(description = "Filters") @RequestParam(name = "filters", required = false) String stringFilters,
                                                                    Pageable pageable) throws JsonProcessingException {
        List<ResourceFilter> resourceFilters = ResourceFilter.fromStringToList(stringFilters);
        Page<FeederResult> feederResultsPage = shortCircuitService.getFeederResultsPage(resultUuid, resourceFilters, pageable);
        return feederResultsPage != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(feederResultsPage)
            : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a short circuit analysis result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result has been deleted")})
    public ResponseEntity<Void> deleteResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        shortCircuitService.deleteResult(resultUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete all short circuit analysis results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All short circuit analysis results have been deleted")})
    public ResponseEntity<Void> deleteResults() {
        shortCircuitService.deleteResults();
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the short circuit analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        String result = shortCircuitService.getStatus(resultUuid);
        return ResponseEntity.ok().body(result);
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the short circuit analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        shortCircuitService.setStatus(resultUuids, ShortCircuitAnalysisStatus.NOT_DONE.name());
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a short circuit analysis computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver) {
        shortCircuitService.stop(resultUuid, receiver);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/fault-types", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get list of fault types")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of fault types")})
    public ResponseEntity<FaultType[]> getFaultTypes() {
        return ResponseEntity.ok().body(FaultType.values());
    }

    @GetMapping(value = "/limit-violation-types", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get list of limit violation types")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of limit violation types")})
    public ResponseEntity<List<LimitViolationType>> getLimitTypes() {
        return ResponseEntity.ok().body(List.of(LimitViolationType.LOW_SHORT_CIRCUIT_CURRENT, LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT));
    }

}
