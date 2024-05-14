/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.shortcircuit.server.computation.service.AbstractComputationObserver;
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

    public <E extends Throwable> void observe(String name, Observation.CheckedRunnable<E> callable) throws E {
        createObservation(name).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name).observeChecked(callable);
    }

    public <T extends ShortCircuitAnalysisResult, E extends Throwable> T observeRun(String name, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservation(name).observeChecked(callable);
        incrementCount(result);
        return result;
    }

    private Observation createObservation(String name) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, ShortCircuitAnalysis.find().getName())
                .lowCardinalityKeyValue(TYPE_TAG_NAME, COMPUTATION_TYPE);
    }

    private void incrementCount(ShortCircuitAnalysisResult result) {
        Counter.builder(COMPUTATION_COUNTER_NAME)
                .tag(PROVIDER_TAG_NAME, ShortCircuitAnalysis.find().getName())
                .tag(TYPE_TAG_NAME, COMPUTATION_TYPE)
                .tag(STATUS_TAG_NAME, getResultStatus(result))
                .register(meterRegistry)
                .increment();
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
