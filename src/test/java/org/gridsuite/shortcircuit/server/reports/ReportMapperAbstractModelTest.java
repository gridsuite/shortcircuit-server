/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeNoOp;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
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
        protected ReportNode forShortCircuitAnalysis(@NonNull ReportNode reportNode, ShortCircuitRunContext runContext) {
            return reportNode;
        }
    };

    @Test
    void testEmptyReporter() {
        assertThat(reportMapper.processReporter(ReportNode.NO_OP, null)).isInstanceOf(ReportNodeNoOp.class).isSameAs(ReportNode.NO_OP);
    }

    @Test
    void testIgnoreOthersReportModels() {
        ReportNode reportNode = ReportNode.newRootReportNode().withMessageTemplate("test", "Test node").build();
        assertThat(reportMapper.processReporter(reportNode, null)).isSameAs(reportNode);
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_REPORTER_ID, ROOT_REPORTER_NO_ID})
    void testModifyRootNode(final String rootId) {
        final ReportNode reportNode = ReportNode.newRootReportNode().withMessageTemplate(rootId, rootId).build();
        final ReportNode subReportNode = reportNode.newReportNode()
            .withMessageTemplate(SHORTCIRCUIT_TYPE_REPORT, SHORTCIRCUIT_TYPE_REPORT + " (${providerToUse})")
            .withUntypedValue("providerToUse", "Courcirc")
            .add();
        assertThat(subReportNode).isInstanceOf(ReportNode.class);

        subReportNode.newReportNode()
            .withMessageTemplate("generatorConversion", "Conversion of generators")
            .add()
            .newReportNode().withMessageTemplate("disconnectedTerminalGenerator", "Regulating terminal of connected generator ${generator} is disconnected. Regulation is disabled.")
                .withUntypedValue("generator", "TestGenerator")
                .add();

        assertThat(reportMapper.processReporter(reportNode, null))
                .isNotSameAs(reportNode)
                .usingRecursiveComparison(ASSERTJ_RECURSIVE_COMPARISON_CONFIGURATION)
                .isEqualTo(reportNode);
    }

    @Test
    void testCopyReportTraceNotAcceptNullArguments() {
        assertThatThrownBy(() -> AbstractReportMapper.copyReportAsTrace(null, ReportNode.newRootReportNode().withMessageTemplate("", "").build()))
                .as("copyReportAsTrace(null, *)")
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AbstractReportMapper.copyReportAsTrace(ReportNode.newRootReportNode().withMessageTemplate("", "").build(), null))
                .as("copyReportAsTrace(*, null)")
                .isInstanceOf(NullPointerException.class);
    }
}
