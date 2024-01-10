/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server;

import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.NonNull;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.cloud.stream.binder.test.OutputDestination;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static com.vladmihalcea.sql.SQLStatementCountValidator.assertDeleteCount;
import static org.junit.Assert.assertNull;

/**
 * @author Franck Lecuyer <frnck.lecuyer at rte-france.com>
 */
public final class TestUtils {
    private static final long TIMEOUT = 100;

    private TestUtils() {
        throw new IllegalStateException("Not implemented exception");
    }

    public static final ShortCircuitRunContext MOCK_RUN_CONTEXT = new ShortCircuitRunContext(
            UUID.randomUUID(),
            null,
            null,
            new ShortCircuitParameters(),
            null,
            null,
            null,
            null,
            null
    );

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> assertNull("Should not be any messages in queue " + destination + " : ", output.receive(TIMEOUT, destination)));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }

    /**
     * Inject a (mocked?) {@link ShortCircuitAnalysisProvider provider} into the {@link ShortCircuitAnalysis.Runner}
     * before it call Java's {@link java.util.ServiceLoader}<br/>
     * @param provider the mock to close after test
     */
    public static MockedStatic<ShortCircuitAnalysis> injectShortCircuitAnalysisProvider(@NonNull final ShortCircuitAnalysisProvider provider) throws Exception {
        // ShortCircuitAnalysis.Runner constructor is private
        final Constructor<ShortCircuitAnalysis.Runner> constructor = ShortCircuitAnalysis.Runner.class.getDeclaredConstructor(ShortCircuitAnalysisProvider.class);
        constructor.setAccessible(true);
        final ShortCircuitAnalysis.Runner runner = constructor.newInstance(provider);
        //MockedStatic is closeable object
        MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisRunnerMockedStatic = null;
        try {
            shortCircuitAnalysisRunnerMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class);
            shortCircuitAnalysisRunnerMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            return shortCircuitAnalysisRunnerMockedStatic;
        } catch (final Exception ex) {
            if (shortCircuitAnalysisRunnerMockedStatic != null) {
                shortCircuitAnalysisRunnerMockedStatic.closeOnDemand();
            }
            throw ex;
        }
    }

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }
}
