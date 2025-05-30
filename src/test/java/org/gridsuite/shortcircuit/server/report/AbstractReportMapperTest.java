/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.report;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.text.StringSubstitutor;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.WithAssertions;
import org.assertj.core.api.recursive.comparison.RecursiveComparator;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.assertj.core.presentation.StandardRepresentation;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.gridsuite.shortcircuit.server.service.ShortCircuitWorkerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.UUID;

/**
 * @see ShortCircuitWorkerService#run(ShortCircuitRunContext, UUID)
 */
@Slf4j
abstract class AbstractReportMapperTest implements WithAssertions {
    protected static final RecursiveComparisonConfiguration ASSERTJ_RECURSIVE_COMPARISON_CONFIGURATION = RecursiveComparisonConfiguration.builder()
            .withIgnoreCollectionOrder(false)
            .withComparatorForType((r1, r2) -> new RecursiveComparator(RecursiveComparisonConfiguration.builder().withIgnoreCollectionOrder(true).withIgnoreAllOverriddenEquals(true).build())
                    .compare(r1, r2), ReportNode.class)
            .withIgnoreAllOverriddenEquals(true)
            .build();

    @BeforeAll
    static void config() {
        Assertions.useRepresentation(new ReportRepresentation());
    }

    @AfterAll
    static void unConfig() {
        Assertions.useDefaultRepresentation();
    }

    /**
     * Replace default {@code toString()} "{@code com.powsybl.commons.reporter.Report@14998e21}" in AssertJ
     * output by the content of the report in assertion messages.
     */
    public static class ReportRepresentation extends StandardRepresentation {
        /** {@inheritDoc} */
        @Override
        public String toStringOf(Object object) {
            if (object instanceof ReportNode reportNode) {
                return "@" + StringUtils.rightPad(Integer.toHexString(System.identityHashCode(reportNode)), 9)
                        + (reportNode.getValues().containsKey("reportSeverity") ? reportNode.getValue("reportSeverity").get().getValue() : "UNKOWN")
                        + " [" + reportNode.getMessageKey() + "] " + StringSubstitutor.replace(reportNode.getMessageTemplate(), reportNode.getValues())
                        + "  #dict@" + hashCodeValues(reportNode.getValues());
            } else {
                return super.toStringOf(object);
            }
        }

        private static String hashCodeValues(final Map<String, TypedValue> map) {
            //TypedValue hasn't an hashCode implemented...  ;  based on AbstractMap and HashMap.Node
            return (map == null) ? "null" : Integer.toHexString(map.entrySet().stream()
                    .mapToInt(e -> java.util.Objects.hashCode(e.getKey()) ^ new HashCodeBuilder(17, 37)
                            .append(e.getValue().getType())
                            .append(e.getValue().getValue())
                            .hashCode())
                    .sum());
        }
    }
}
