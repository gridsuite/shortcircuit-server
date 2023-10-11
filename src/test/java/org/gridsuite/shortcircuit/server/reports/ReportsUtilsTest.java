/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.WithAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ExtendWith({ SoftAssertionsExtension.class })
@Slf4j
class ReportsUtilsTest implements WithAssertions {
    @Test
    void testEqualsTypedValue() {
        final TypedValue tv = new TypedValue("val", "tip");
        assertThat(ReportsUtils.equalsTypedValue(tv, tv)).as("same object equals").isTrue();
        assertThat(ReportsUtils.equalsTypedValue(tv, new TypedValue("val", "tip"))).as("clone object equals").isTrue();
        assertThat(ReportsUtils.equalsTypedValue(tv, new TypedValue("obj", "tip"))).as("not equals objects").isFalse();
        assertThat(ReportsUtils.equalsTypedValue(null, tv)).as("first null").isFalse();
        assertThat(ReportsUtils.equalsTypedValue(tv, null)).as("second null").isFalse();
        assertThat(ReportsUtils.equalsTypedValue(null, null)).as("two null equals").isTrue();
    }

    @Test
    void testReportsNullArguments() {
        assertThatThrownBy(() -> ReportsUtils.copyReportAsTrace(new ReporterModel("test", "test"), null))
                .as("With null report")
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ReportsUtils.copyReportAsTrace(null, new Report("", "", Map.of())))
                .as("With null reporter")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testReportsTransformedToTraceLevel() {
        ReporterForTest reporter = new ReporterForTest("Test", "test node", Map.of());
        final Report report = new Report("Test1", "test", Map.of(Report.REPORT_SEVERITY_KEY, TypedValue.INFO_SEVERITY));
        ReportsUtils.copyReportAsTrace(reporter, report);
        assertThat(reporter).as("reporter test")
                .extracting(r -> r.reports, InstanceOfAssertFactories.list(Report.class)).as("reports")
                .singleElement()
                // Report doesn't implement equals() method...
                .hasFieldOrPropertyWithValue("reportKey", "Test1")
                .hasFieldOrPropertyWithValue("defaultMessage", "test")
                .extracting(Report::getValues, InstanceOfAssertFactories.MAP)
                .hasSize(1)
                .containsEntry(Report.REPORT_SEVERITY_KEY, TypedValue.TRACE_SEVERITY);
    }

    @Test
    void testReportsAlreadyAtTraceLevelIgnored(final SoftAssertions softly) {
        ReporterForTest reporter = new ReporterForTest("Test", "test node", Map.of());
        {
            final Report report = new Report("Test1", "test", Map.of(Report.REPORT_SEVERITY_KEY, TypedValue.TRACE_SEVERITY));
            ReportsUtils.copyReportAsTrace(reporter, report);
            softly.assertThat(reporter).as("reporter test")
                    .extracting(r -> r.reports, InstanceOfAssertFactories.list(Report.class)).as("reports")
                    .singleElement()
                    .isSameAs(report);
            reporter.reset();
        }
        {
            final Report report = Report.builder().withSeverity(TypedValue.TRACE_SEVERITY).withKey("Test2").withDefaultMessage("test").build();
            ReportsUtils.copyReportAsTrace(reporter, report);
            softly.assertThat(reporter).as("reporter test")
                    .extracting(r -> r.reports, InstanceOfAssertFactories.list(Report.class)).as("reports")
                    .singleElement()
                    .isSameAs(report);
            reporter.reset();
        }
        {
            final Report report = new Report("Test3", "test", Map.of(Report.REPORT_SEVERITY_KEY, new TypedValue("TRACE", TypedValue.SEVERITY)));
            ReportsUtils.copyReportAsTrace(reporter, report);
            softly.assertThat(reporter).as("reporter test")
                    .extracting(r -> r.reports, InstanceOfAssertFactories.list(Report.class)).as("reports")
                    .singleElement()
                    .isSameAs(report);
        }
    }

    private static class ReporterForTest extends ReporterModel {
        public List<Report> reports = new ArrayList<>();

        public ReporterForTest(String taskKey, String defaultName, Map<String, TypedValue> taskValues) {
            super(taskKey, defaultName, taskValues);
        }

        @Override
        public ReporterModel createSubReporter(String taskKey, String defaultName, Map<String, TypedValue> values) {
            throw new UnsupportedOperationException("Not implemented in test");
        }

        @Override
        public void addSubReporter(ReporterModel reporterModel) {
            throw new UnsupportedOperationException("Not implemented in test");
        }

        @Override
        public void report(Report report) {
            this.reports.add(report);
        }

        public void reset() {
            this.reports.clear();
        }
    }
}
