/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import com.powsybl.ws.commons.computation.service.AbstractComputationObserver;
import org.springframework.stereotype.Service;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitObserver extends AbstractComputationObserver<ShortCircuitAnalysisResult, ShortCircuitParameters> {

    private static final String COMPUTATION_TYPE = "shortcircuitanalysis";

    public ShortCircuitObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry);
    }

    @Override
    protected String getResultStatus(ShortCircuitAnalysisResult result) {
        return result != null ? "OK" : "NOK";
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }
}
