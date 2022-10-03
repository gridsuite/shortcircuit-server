/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
public class ShortCircuitRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final List<UUID> otherNetworkUuids;

    private final List<UUID> variablesFiltersListUuids;

    private final List<UUID> contingencyListUuids;

    private final List<UUID> branchFiltersListUuids;

    private final String receiver;

    private final String provider;

    private final ShortCircuitParameters parameters;

    private final UUID reportUuid;

    public ShortCircuitRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids,
                                  List<UUID> variablesFiltersListUuids, List<UUID> contingencyListUuids,
                                  List<UUID> branchFiltersListUuids,
                                  String receiver, String provider, ShortCircuitParameters parameters, UUID reportUuid) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.variablesFiltersListUuids = Objects.requireNonNull(variablesFiltersListUuids);
        this.contingencyListUuids = Objects.requireNonNull(contingencyListUuids);
        this.branchFiltersListUuids = Objects.requireNonNull(branchFiltersListUuids);
        this.receiver = receiver;
        this.provider = provider;
        this.parameters = Objects.requireNonNull(parameters);
        this.reportUuid = reportUuid;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }

    public String getVariantId() {
        return variantId;
    }

    public List<UUID> getOtherNetworkUuids() {
        return otherNetworkUuids;
    }

    public List<UUID> getVariablesFiltersListUuids() {
        return variablesFiltersListUuids;
    }

    public List<UUID> getContingencyListUuids() {
        return contingencyListUuids;
    }

    public List<UUID> getBranchFiltersListUuids() {
        return branchFiltersListUuids;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getProvider() {
        return provider;
    }

    public ShortCircuitParameters getParameters() {
        return parameters;
    }

    public UUID getReportUuid() {
        return reportUuid;
    }
}
