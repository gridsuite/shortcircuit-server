/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.powsybl.shortcircuit.Fault;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + ShortCircuitApi.API_VERSION)
@Tag(name = "Short circuit server")
public class ShortCircuitController {
    private final ShortCircuitService service;

    public ShortCircuitController(ShortCircuitService service) {
        this.service = service;
    }

    private static ShortCircuitParameters getNonNullParameters(ShortCircuitParameters parameters) {
        return parameters != null ? parameters : new ShortCircuitParameters();
    }

    @PostMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a shortcut circuit on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
                                        description = "The short circuit analysis has been performed",
                                        content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = ShortCircuitParameters.class))})}
    )
    public ResponseEntity<ShortCircuitAnalysisResult> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                         @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                         @Parameter(description = "Other networks UUID (to merge with main one))") @RequestParam(name = "networkUuid", required = false) List<UUID> otherNetworkUuids,
                                                         @Parameter(description = "Faults") @RequestParam(name = "faults", required = false) List<Fault> faults,
                                                         @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                                         @RequestBody(required = false) ShortCircuitParameters parameters) {
        ShortCircuitParameters nonNullParameters = getNonNullParameters(parameters);
        ShortCircuitAnalysisResult result = service.run(networkUuid, variantId, otherNetworkUuids, faults, reportUuid, nonNullParameters);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
