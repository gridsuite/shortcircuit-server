/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.mutable.MutableLong;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.AbstractComputationRunContext;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersValues;

import java.util.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitRunContext extends AbstractComputationRunContext<ShortCircuitParametersValues> {
    @Setter
    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
    private final String busId;
    @Setter
    private List<String> voltageLevelsWithWrongIsc = new ArrayList<>();
    private final UUID parametersUuid;
    private final UUID resultUuid;

    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterGenerator = new MutableLong();
    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterBattery = new MutableLong();
    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterLines = new MutableLong();
    /** @see org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper */
    private final MutableLong adnSummarizeCounterT2W = new MutableLong();

    @Builder
    public ShortCircuitRunContext(UUID networkUuid, String variantId, String receiver, ShortCircuitParametersValues parameters, UUID parametersUuid,
                                   ReportInfos reportInfos, String userId, String provider, String busId, Boolean debug, UUID resultUuid) {
        super(networkUuid, variantId, receiver, reportInfos, userId, provider, parameters, debug);
        this.busId = busId;
        this.parametersUuid = parametersUuid;
        this.resultUuid = resultUuid;
    }

    public ShortCircuitParameters buildParameters() {
        ShortCircuitParameters params = getParameters() == null || getParameters().commonParameters() == null ?
                ShortCircuitParameters.load() : getParameters().commonParameters();
        if (getParameters() == null || getParameters().specificParameters() == null || getParameters().specificParameters().isEmpty()) {
            return params; // no specific ShortCircuit params
        }
        return params;
    }
}
