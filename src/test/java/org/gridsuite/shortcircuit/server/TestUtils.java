/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
import lombok.NonNull;

import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersValues;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.cloud.stream.binder.test.OutputDestination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Franck Lecuyer <frnck.lecuyer at rte-france.com>
 */
public final class TestUtils {
    private static final long TIMEOUT = 100;

    /**
     * Matcher for {@link java.util.UUID UUID v4}.
     */
    public static final Pattern UUID_V4 = Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}", Pattern.CASE_INSENSITIVE);
    public static final Pattern UUID_IN_JSON = Pattern.compile("^\"" + UUID_V4.pattern() + "\"$", Pattern.CASE_INSENSITIVE);

    private TestUtils() {
        throw new IllegalStateException("Not implemented exception");
    }

    public static final ShortCircuitRunContext MOCK_RUN_CONTEXT = new ShortCircuitRunContext(
            UUID.randomUUID(),
            null,
            null,
            ShortCircuitParametersValues.builder().build(),
            null,
            null,
            null,
            ShortCircuitParametersConstants.DEFAULT_PROVIDER,  // TODO : replace with null when fix in powsybl-ws-commons will handle null provider
            null,
            false,
            null
    );

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> assertNull(output.receive(TIMEOUT, destination), "Should not be any messages in queue " + destination + " : "));
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

    public static byte[] unzip(byte[] zippedBytes) throws Exception {
        var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zippedBytes));
        var buff = new byte[1024];
        if (zipInputStream.getNextEntry() != null) {
            var outputStream = new ByteArrayOutputStream();
            int l;
            while ((l = zipInputStream.read(buff)) > 0) {
                outputStream.write(buff, 0, l);
            }
            return outputStream.toByteArray();
        }
        return new byte[0];
    }
}
