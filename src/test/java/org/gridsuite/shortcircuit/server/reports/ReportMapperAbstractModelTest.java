/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class ReportMapperAbstractModelTest extends AbstractReportMapperTest {
    static final String ROOT_REPORTER_ID = "00000000-0000-0000-0000-000000000000@ShortCircuitAnalysis";
    static final String ROOT_REPORTER_NO_ID = "ShortCircuitAnalysis";
    static final String SHORTCIRCUIT_TYPE_REPORT = "ShortCircuitAnalysis";

    private final AbstractReportMapper reportMapper = new AbstractReportMapper() {
        @Override
        protected ReporterModel forShortCircuitAnalysis(@NonNull ReporterModel reporterModel) {
            return reporterModel;
        }
    };

    @Test
    void testEmptyReporter() {
        assertThat(reportMapper.processReporter(Reporter.NO_OP)).isInstanceOf(Reporter.NoOpImpl.class).isSameAs(Reporter.NO_OP);
    }

    @Test
    void testIgnoreOthersReportModels() {
        final Reporter reporter = new ReporterModel("test", "Test node");
        assertThat(reportMapper.processReporter(reporter)).isSameAs(reporter);
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_REPORTER_ID, ROOT_REPORTER_NO_ID})
    void testModifyRootNode(final String rootId) {
        final Reporter targetReporter = new ReporterModel(rootId, rootId);
        final Reporter reporter = targetReporter.createSubReporter(SHORTCIRCUIT_TYPE_REPORT, SHORTCIRCUIT_TYPE_REPORT + " (${providerToUse})", "providerToUse", "Courcirc");
        assertThat(reporter).isInstanceOf(ReporterModel.class);
        reporter.createSubReporter("generatorConversion", "Conversion of generators")
                .report("disconnectedTerminalGenerator", "Regulating terminal of connected generator ${generator} is disconnected. Regulation is disabled.", "generator", "TestGenerator");
        assertThat(reportMapper.processReporter(targetReporter))
                .isNotSameAs(targetReporter)
                .usingRecursiveComparison(ASSERTJ_RECURSIVE_COMPARISON_CONFIGURATION)
                .isEqualTo(targetReporter);
    }
}
