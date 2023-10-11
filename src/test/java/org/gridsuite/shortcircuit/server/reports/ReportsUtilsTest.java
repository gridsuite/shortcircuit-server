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
    void testReportsTransformedToTraceLevel() {
        ReporterForTest reporter = new ReporterForTest("Test", "test node", Map.of());
        final Report report = new Report("Test1", "test", Map.of(Report.REPORT_SEVERITY_KEY, TypedValue.TRACE_SEVERITY));
        ReportsUtils.copyReportAsTrace(reporter, report);
        assertThat(reporter).as("reporter test")
                .extracting(r -> r.reports, InstanceOfAssertFactories.list(Report.class)).as("reports")
                .singleElement()
                .isSameAs(report);
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
