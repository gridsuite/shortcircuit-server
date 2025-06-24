/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeDeserializer;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.RestTemplateConfig;
import org.gridsuite.shortcircuit.server.report.mappers.VoltageLevelsWithWrongIpValuesMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Slf4j
class ReportMapperShortCircuitTest extends AbstractReportMapperTest {
    private static final ObjectMapper OBJECT_MAPPER = RestTemplateConfig.objectMapper();
    private ReportMapperService reportMapperService;
    private ShortCircuitRunContext runContext;

    @BeforeEach
    void prepare() {
        final MapperBeans beansInit = new MapperBeans();
        this.reportMapperService = new ReportMapperService(List.of(
                beansInit.powsyblAdnGeneratorsAndBatteriesSeverity(),
                beansInit.powsyblAdnLinesSeverity(),
                beansInit.powsyblAdnTwoWindingsTransformersSeverity(),
                beansInit.powsyblAdnGeneratorsSummary(),
                beansInit.powsyblAdnBatteriesSummary(),
                beansInit.powsyblAdnLinesSummary(),
                beansInit.powsyblAdnTwoWindingsTransformersSummary(),
                new VoltageLevelsWithWrongIpValuesMapper()
        ));
        this.runContext = new ShortCircuitRunContext(null, "variantId", "receiver", new ShortCircuitParameters(),
                null, "reporterId", "reportType", "userId", "default-provider", "busId");
    }

    @ParameterizedTest(name = "reporter_{0}.json")
    @ValueSource(strings = {"shortcircuit", "adn"})
    void testAggregatedLogs(final String partFileName) throws Exception {
        ReportNode rootReportNode;
        try (final InputStream in = ReportMapperShortCircuitTest.class.getClassLoader().getResourceAsStream("reporter_" + partFileName + "_test.json")) {
            rootReportNode = ReportNodeDeserializer.read(in);
        }
        // The problem here is that ResourceBundles aren't loaded when deserialized, so when modifying the tree,
        //   we get in dictionary values "Cannot find message template with key: 'theMessageKey'".
        // The only solution found is to manually do `withResourceBundles(ShortcircuitServerReportResourceBundle.BASE_NAME)` when creating a new node.

        this.runContext.setVoltageLevelsWithWrongIsc(Collections.singletonList("VL1"));
        final ReportNode result = reportMapperService.map(rootReportNode, this.runContext);

        log.debug("Result = {}", OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        // strict=false -> no-order & extensible => reportTimestamp not in json/expected will not fail
        JSONAssert.assertEquals("short-circuit logs aggregated",
                Files.readString(Paths.get(this.getClass().getClassLoader().getResource("reporter_" + partFileName + "_modified.json").toURI())),
                OBJECT_MAPPER.writeValueAsString(result),
                false);
    }
}
