/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.shortcircuit.server.computation.dto.ReportInfos;
import org.gridsuite.shortcircuit.server.computation.service.AbstractComputationRunContext;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;

import java.util.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext extends AbstractComputationRunContext<ShortCircuitParameters> {

    @Setter
    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
    private String busId;
    @Setter
    private List<String> inconsistentVoltageLevels = new ArrayList<>();

    public ShortCircuitRunContext(UUID networkUuid, String variantId, String receiver, ShortCircuitParameters parameters,
                                  UUID reportUuid, String reporterId, String reportType, String userId, String provider, String busId) {
        super(networkUuid, variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId, provider, parameters, ReportNode.NO_OP);
        this.busId = busId;
    }
}
