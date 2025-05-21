/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.report;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeNoOp;
import com.powsybl.commons.report.TypedValue;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ReportMapperServiceTest extends AbstractReportMapperTest {
    private static final String ROOT_REPORTER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String SHORTCIRCUIT_TYPE_REPORT = "ShortCircuitAnalysis";

    private final ShortCircuitRunContext runContext = new ShortCircuitRunContext(null, null, null, null, null, null, null, null, null, null);
    @Mock ReportMapper reportMapper;
    private ReportMapperService reportMapperService;

    @BeforeEach
    void setUp() {
        reportMapperService = new ReportMapperService(List.of(reportMapper));
    }

    @AfterEach
    void tearDown() {
        Mockito.verifyNoMoreInteractions(reportMapper);
    }

    @Test
    void testEmptyReporter() {
        assertThat(reportMapperService.map(ReportNode.NO_OP, runContext)).isInstanceOf(ReportNodeNoOp.class).isSameAs(ReportNode.NO_OP);
        Mockito.verifyNoInteractions(reportMapper);
    }

    @Test
    void testNoMapper() {
        final ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("testNode").build();
        final List<ReportMapper> reportMappers = Mockito.mock(List.class);
        assertThat(new ReportMapperService(reportMappers).map(reportNode, runContext)).isSameAs(reportNode);
        Mockito.verifyNoInteractions(reportMappers);
    }

    @Test
    void testIgnoreOthersReportModels() {
        final ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("testNode").build();
        assertThat(reportMapperService.map(reportNode, runContext)).isSameAs(reportNode);
    }

    @ParameterizedTest
    @ValueSource(strings = {ROOT_REPORTER_ID})
    void testModifyRootNode(final String rootId) {
        final ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports", "com.powsybl.ws.commons.reports")
                .withMessageTemplate("ws.commons.rootReporterId")
                .withTypedValue("rootReporterId", rootId, TypedValue.ID).build();
        final ReportNode subReportNode = reportNode.newReportNode()
                .withMessageTemplate("ws.commons.reportType")
                .withUntypedValue("reportType", SHORTCIRCUIT_TYPE_REPORT)
                .withUntypedValue("optionalProvider", " (Courcirc)")
                .add();
        final ReportNode childReportNode = subReportNode.newReportNode()
                .withMessageTemplate("generatorConversion")
                .add();
        final ReportNode grandchildReportNode = childReportNode.newReportNode()
                .withMessageTemplate("disconnectedTerminalGenerator")
                .withUntypedValue("generator", "TestGenerator")
                .add();

        // rebuild the same tree to compare if it has been modified
        final ReportNode reportNodeToCompare = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports", "com.powsybl.ws.commons.reports")
                .withMessageTemplate("ws.commons.rootReporterId")
                .withTypedValue("rootReporterId", rootId, TypedValue.ID).build();
        reportNodeToCompare.newReportNode()
                .withMessageTemplate("ws.commons.reportType")
                .withUntypedValue("reportType", SHORTCIRCUIT_TYPE_REPORT)
                .withUntypedValue("optionalProvider", " (Courcirc)")
                .add()
                .newReportNode()
                .withMessageTemplate("generatorConversion")
                .add()
                .newReportNode().withMessageTemplate("disconnectedTerminalGenerator")
                .withUntypedValue("generator", "TestGenerator")
                .add();
        assertThat(reportMapperService.map(reportNode, runContext))
                .isSameAs(reportNode)
                .satisfies(rNode -> assertThat(rNode)
                    .extracting(ReportNode::getChildren, InstanceOfAssertFactories.list(ReportNode.class)).as("root children")
                    .singleElement().as("subReportNode").isSameAs(subReportNode)
                    .extracting(ReportNode::getChildren, InstanceOfAssertFactories.list(ReportNode.class)).as("subroot children")
                    .singleElement().as("childReportNode").isSameAs(childReportNode)
                    .extracting(ReportNode::getChildren, InstanceOfAssertFactories.list(ReportNode.class)).as("child's children")
                    .singleElement().as("grandchildReportNode").isSameAs(grandchildReportNode)
                )
                .usingRecursiveComparison(ASSERTJ_RECURSIVE_COMPARISON_CONFIGURATION)
                .isEqualTo(reportNodeToCompare);
        Mockito.verify(reportMapper).transformNode(Mockito.same(reportNode), Mockito.same(runContext));
        Mockito.verify(reportMapper).transformNode(Mockito.same(subReportNode), Mockito.same(runContext));
        Mockito.verify(reportMapper).transformNode(Mockito.same(childReportNode), Mockito.same(runContext));
        Mockito.verify(reportMapper).transformNode(Mockito.same(grandchildReportNode), Mockito.same(runContext));
    }
}
