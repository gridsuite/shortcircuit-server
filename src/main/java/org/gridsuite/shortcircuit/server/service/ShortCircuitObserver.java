/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitObserver {
    private static final String OBSERVATION_PREFIX = "app.computation.";
    private static final String TYPE_TAG_NAME = "type";
    private static final String STATUS_TAG_NAME = "status";
    private static final String COMPUTATION_TYPE = "shortcircuitanalysis";
    private static final String COMPUTATION_COUNTER_NAME = OBSERVATION_PREFIX + "count";
    private static final String PROVIDER_TAG_NAME = "provider";
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public ShortCircuitObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> void observe(String name, Observation.CheckedRunnable<E> runnable) throws E {
        createObservation(name).observeChecked(runnable);
    }

    public <T extends ShortCircuitAnalysisResult, E extends Throwable> T observeRun(String name, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservation(name).observeChecked(callable);
        incrementCount(result);
        return result;
    }

    public <T, E extends Throwable> T observe(String name, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name).observeChecked(callable);
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
                .tag(STATUS_TAG_NAME, result != null ? "OK" : "NOK")
                .register(meterRegistry)
                .increment();
    }
}
