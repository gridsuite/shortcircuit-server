/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeImpl;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.RestTemplateConfig;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
class ReportMapperShortCircuitTest extends AbstractReportMapperTest {
    private final AbstractReportMapper reportMapper = new ReportMapperShortCircuit();
    protected static ReportNode rootReportNode;

    @BeforeAll
    static void prepare() throws IOException {
        rootReportNode = RestTemplateConfig.objectMapper().readValue(AbstractReportMapperTest.class.getClassLoader().getResource("reporter_shortcircuit_test.json"), ReportNodeImpl.class);
    }

    @Test
    void testAggregatedLogs() throws IOException, URISyntaxException, JSONException {
        final ReportNode result = reportMapper.processReporter(rootReportNode);
        log.debug("Result = {}", RestTemplateConfig.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        JSONAssert.assertEquals("short-circuit logs aggregated",
                Files.readString(Paths.get(this.getClass().getClassLoader().getResource("reporter_shortcircuit_modified.json").toURI())),
                RestTemplateConfig.objectMapper().writeValueAsString(result),
                false);
    }
}
