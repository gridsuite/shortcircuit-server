/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.RestTemplateConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;

@Slf4j
class ReportMapperCourcircTest extends AbstractReportMapperTest {
    private final ReportMapper/*Courcirc*/ reportMapper = new ReportMapperCourcirc();
    protected static ReporterModel rootReporter;

    @BeforeAll
    static void prepare() throws IOException {
        rootReporter = RestTemplateConfig.objectMapper().readValue(AbstractReportMapperTest.class.getClassLoader().getResource("reporter_courcirc_test.json"), ReporterModel.class);
    }

    @Test
    void testAggregatedLogs() throws IOException {
        final ReporterModel targetReporter = RestTemplateConfig.objectMapper().readValue(ReportMapperCourcircTest.class.getClassLoader().getResource("reporter_courcirc_modified.json"), ReporterModel.class);
        final Reporter result = reportMapper.processReporter(rootReporter);
        log.debug("Result = {}", Jackson2ObjectMapperBuilder.json().findModulesViaServiceLoader(true).build().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        assertThat(result)
                .isNotSameAs(rootReporter)
                .usingRecursiveComparison(ASSERTJ_RECURSIVE_COMPARISON_CONFIGURATION)
                .isEqualTo(targetReporter);
    }
}
