/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final List<UUID> otherNetworkUuids;

    private final String receiver;

    private final ShortCircuitParameters parameters;

    private final UUID reportUuid;

    public ShortCircuitRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids, String receiver, ShortCircuitParameters parameters, UUID reportUuid) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.receiver = receiver;
        this.parameters = Objects.requireNonNull(parameters);
        this.reportUuid = reportUuid;
    }
}
