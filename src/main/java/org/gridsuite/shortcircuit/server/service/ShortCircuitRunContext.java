/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final String receiver;

    private final ShortCircuitParameters parameters;

    private final UUID reportUuid;

    private final String reporterId;

    private final String userId;

    private final String busId;

    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();

    public ShortCircuitRunContext(UUID networkUuid, String variantId, String receiver, ShortCircuitParameters parameters, UUID reportUuid, String reporterId, String userId, String busId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.receiver = receiver;
        this.parameters = Objects.requireNonNull(parameters);
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
        this.userId = userId;
        this.busId = busId;
    }

    public void setShortCircuitLimits(Map<String, ShortCircuitLimits> shortCircuitLimits) {
        this.shortCircuitLimits = shortCircuitLimits;
    }
}
