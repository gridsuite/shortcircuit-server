/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import com.powsybl.ws.commons.computation.service.AbstractComputationRunContext;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.mutable.MutableLong;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;

import java.util.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext extends AbstractComputationRunContext<ShortCircuitParameters> {
    @Setter
    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
    private final String busId;
    @Setter
    private List<String> voltageLevelsWithWrongIsc = new ArrayList<>();

    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterGenerator =  new MutableLong();
    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterBattery =  new MutableLong();
    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterT2W =  new MutableLong();

    public ShortCircuitRunContext(UUID networkUuid, String variantId, String receiver, ShortCircuitParameters parameters,
                                  UUID reportUuid, String reporterId, String reportType, String userId, String provider, String busId) {
        super(networkUuid, variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId, provider, parameters);
        this.busId = busId;
    }
}
