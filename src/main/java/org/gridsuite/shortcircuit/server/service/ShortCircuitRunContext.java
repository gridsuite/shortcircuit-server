/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
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
        ShortCircuitParameters params = getParameters() == null || getParameters().getCommonParameters() == null ?
                ShortCircuitParameters.load() : getParameters().getCommonParameters();
        if (getParameters() == null || getParameters().getSpecificParameters() == null || getParameters().getSpecificParameters().isEmpty()) {
            return params; // no specific ShortCircuit params
        }
        ShortCircuitAnalysisProvider scProvider = ShortCircuitAnalysisProvider.findAll().stream()
                .filter(p -> p.getName().equals(getProvider()))
                .findFirst().orElseThrow(() -> new PowsyblException("ShortCircuit provider not found " + getProvider()));

        Extension<ShortCircuitParameters> specificParametersExtension = scProvider.loadSpecificParameters(PlatformConfig.defaultConfig())
                .orElseThrow(() -> new PowsyblException("Cannot add specific shortcircuit parameters with provider " + getProvider()));
        params.addExtension((Class) specificParametersExtension.getClass(), specificParametersExtension);
        // convert specific parameters values to Map<String,String> expected by updateSpecificParameters
        Map<String, String> specificParams = new HashMap<>();
        getParameters().getSpecificParameters().forEach((k, v) -> specificParams.put(k, v == null ? null : v.toString()));
        // TODO There is a problem here, it doesn't work, it doesn't update extension values
        scProvider.updateSpecificParameters(specificParametersExtension, specificParams);

        return params;
    }
}
