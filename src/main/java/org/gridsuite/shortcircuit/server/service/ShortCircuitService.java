/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitService {

    private NetworkStoreService networkStoreService;
    private ObjectMapper objectMapper;

    public ShortCircuitService(NetworkStoreService networkStoreService, ObjectMapper objectMapper) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ShortCircuitAnalysisResult run(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids, List<Fault> faults, UUID reportUuid, ShortCircuitParameters shortCircuitParameters) {
        Network network = getNetwork(networkUuid, otherNetworkUuids, variantId);
        Reporter reporter = reportUuid != null ? new ReporterModel("ShortCircuit", "Short circuit") : Reporter.NO_OP;
        ShortCircuitAnalysisResult result = ShortCircuitAnalysis.run(network, faults, shortCircuitParameters, LocalComputationManager.getDefault(), List.of(), reporter);
        return result;
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    private Network getNetwork(UUID networkUuid, List<UUID> otherNetworkUuids, String variantId) {
        Network network = getNetwork(networkUuid, variantId);
        if (otherNetworkUuids.isEmpty()) {
            return network;
        } else {
            List<Network> otherNetworks = otherNetworkUuids.stream().map(uuid -> getNetwork(uuid, variantId)).collect(Collectors.toList());
            List<Network> networks = new ArrayList<>();
            networks.add(network);
            networks.addAll(otherNetworks);
            MergingView mergingView = MergingView.create("merge", "iidm");
            mergingView.merge(networks.toArray(new Network[0]));
            return mergingView;
        }
    }
}
