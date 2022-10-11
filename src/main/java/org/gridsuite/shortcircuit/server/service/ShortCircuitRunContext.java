/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.Fault;
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

    private final List<Fault> faults;

    private final String receiver;

    private final ShortCircuitParameters parameters;

    private final UUID reportUuid;

    public ShortCircuitRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids,
                                  List<Fault> faults, String receiver, ShortCircuitParameters parameters, UUID reportUuid) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.faults = faults;
        this.receiver = receiver;
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

    public ShortCircuitParameters getParameters() {
        return parameters;
    }

    public UUID getReportUuid() {
        return reportUuid;
    }

    public List<Fault> getFaults() {
        return faults;
    }

    public String getReceiver() {
        return receiver;
    }
}
