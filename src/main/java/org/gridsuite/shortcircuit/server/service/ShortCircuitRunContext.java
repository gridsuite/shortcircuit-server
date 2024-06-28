/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import com.powsybl.ws.commons.computation.service.AbstractComputationRunContext;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext extends AbstractComputationRunContext<ShortCircuitParameters> {

    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
    private String busId;

    public ShortCircuitRunContext(UUID networkUuid, String variantId, String receiver, ShortCircuitParameters parameters,
                                  UUID reportUuid, String reporterId, String reportType, String userId, String provider, String busId) {
        super(networkUuid, variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId, provider, parameters);
        this.busId = busId;
    }

    public void setShortCircuitLimits(Map<String, ShortCircuitLimits> shortCircuitLimits) {
        this.shortCircuitLimits = shortCircuitLimits;
    }
}
