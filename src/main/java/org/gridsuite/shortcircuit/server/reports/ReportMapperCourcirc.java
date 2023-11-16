/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * This class manages how to postprocess reports of the proprietary courcir simulator to reduce the number of reports
 * by aggregating them.
 * <br/>
 * The tree structure returned by the courcirc proprietary simulator is:
 * <pre>
 * 00000000-0000-0000-0000-000000000000@ShortCircuitAnalysis  or  ShortCircuitAnalysis
 * \-- ShortCircuitAnalysis -> "ShortCircuitAnalysis (${providerToUse})"
 *     +-- [...]
 *     \--courcirc -> "Logs generated by Courcirc simulator"
 * </pre>
 *
 * @implNote as the tree to aggregate seems relatively simple, a simple utility class will suffice
 *
 * @see com.powsybl.commons.reporter.Reporter
 * @see com.powsybl.commons.reporter.ReporterModel
 * @see com.powsybl.commons.reporter.Report
 */
@Slf4j
@Component
public class ReportMapperCourcirc extends AbstractReportMapper {
    /**
     * {@inheritDoc}
     */
    @Override
    protected Reporter forUuidAtShortCircuitAnalysis(@NonNull final ReporterModel reporterModel) {
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getReports().forEach(newReporter::report);
        reporterModel.getSubReporters().forEach(reporter -> {
            if ("ShortCircuitAnalysis".equals(reporter.getTaskKey())
                && "Courcirc".equals(reporter.getTaskValues().getOrDefault("providerToUse", new TypedValue("", "")).getValue())) {
                newReporter.addSubReporter(forShortCircuitAnalysis(reporter));
            } else {
                newReporter.addSubReporter(reporter);
            }
        });
        return newReporter;
    }

    /**
     * Modify node with key {@code ShortCircuitAnalysis}
     *
     * @implNote we assume there will always be at least one modification
     */
    @Override
    protected ReporterModel forShortCircuitAnalysis(@NonNull final ReporterModel reporterModel) {
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getReports().forEach(newReporter::report);
        reporterModel.getSubReporters().forEach(reporter -> newReporter.addSubReporter(
            switch (reporter.getTaskKey()) {
                case "courcirc" -> forCourcirc(reporter);
                default -> reporter;
            }));
        return newReporter;
    }

    /**
     * Modify node with key {@code courcirc}
     * <br/>
     * The relevant part of the tree structure created by the courcirc proprietary simulator output is:
     * <pre>
     * 00000000-0000-0000-0000-000000000000@ShortCircuitAnalysis
     * \-- ShortCircuitAnalysis -> "ShortCircuitAnalysis (${providerToUse})"
     *     \-- courcirc -> "Logs generated by Courcirc simulator"
     * </pre>
     * this courcirc reporter, among other useful reports, contains hundreds of reports.
     *
     * @apiNote the main goal here is to concat specific log lines with a huge number of repetitions into one summary line
     *          and change existing lines found to {@code TRACE} severity
     * @implNote courcirc logs don't seem to use enumerate keys neither template value, only incremental keys (REC_0,REC_1,...),
     *           so we need to test the message
     * @implNote we use {@link ReportWrapper} to insert a {@link Report} without knowing the exact content at that time, and
     *           filling it later
     */
    private ReporterModel forCourcirc(@NonNull final ReporterModel reporterModel) {
        log.trace("courcirc logs detected, will analyse them...");
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getSubReporters().forEach(newReporter::addSubReporter);

        /* preparing */
        long logsTransientReactanceTooLowCount = 0L;
        ReportWrapper logsTransientReactanceTooLowSummary = null;
        TypedValue logsTransientReactanceTooLowSeverity = null;

        long logsTransientReactanceUndefinedCount = 0L;
        ReportWrapper logsTransientReactanceUndefinedSummary = null;
        TypedValue logsTransientReactanceUndefinedSeverity = null;

        long logsSimulatingShortCircuitLocatedCount = 0L;
        ReportWrapper logsSimulatingShortCircuitLocatedSummary = null;
        TypedValue logsSimulatingShortCircuitLocatedSeverity = null;

        long logsShortCircuitNotSimulatedCount = 0L;
        ReportWrapper logsShortCircuitNotSimulatedSummary = null;
        TypedValue logsShortCircuitNotSimulatedSeverity = null;

        /* analyze and compute logs in one pass */
        for (final Report report : reporterModel.getReports()) { //we modify logs conditionally here
            if (StringUtils.endsWith(report.getDefaultMessage(), " : transient reactance too low ==> generator ignored")) {
                //we match line "X.ABCDEF1 : transient reactance too low ==> generator ignored"
                if (logsTransientReactanceTooLowSummary == null) {
                    logsTransientReactanceTooLowSummary = new ReportWrapper();
                    newReporter.report(logsTransientReactanceTooLowSummary);
                    logsTransientReactanceTooLowSeverity = report.getValue(Report.REPORT_SEVERITY_KEY);
                }
                copyReportAsTrace(newReporter, report);
                logsTransientReactanceTooLowCount++;
            } else if (StringUtils.endsWith(report.getDefaultMessage(), " : transient reactance undefined ==> generator ignored")) {
                //we match line "X.ABCDEF2 : transient reactance undefined ==> generator ignored"
                if (logsTransientReactanceUndefinedSummary == null) {
                    logsTransientReactanceUndefinedSummary = new ReportWrapper();
                    newReporter.report(logsTransientReactanceUndefinedSummary);
                    logsTransientReactanceUndefinedSeverity = report.getValue(Report.REPORT_SEVERITY_KEY);
                }
                copyReportAsTrace(newReporter, report);
                logsTransientReactanceUndefinedCount++;
            } else if (StringUtils.startsWith(report.getDefaultMessage(), "Simulating : short-circuit located on node ")) {
                //we match line "Simulating : short-circuit located on node .BRIDGE_0"
                if (logsSimulatingShortCircuitLocatedSummary == null) {
                    logsSimulatingShortCircuitLocatedSummary = new ReportWrapper();
                    newReporter.report(logsSimulatingShortCircuitLocatedSummary);
                    logsSimulatingShortCircuitLocatedSeverity = report.getValue(Report.REPORT_SEVERITY_KEY);
                }
                copyReportAsTrace(newReporter, report);
                logsSimulatingShortCircuitLocatedCount++;
            } else if (StringUtils.startsWith(report.getDefaultMessage(), "Short circuit on node ")
                    && StringUtils.endsWith(report.getDefaultMessage(), " is not simulated : it is located in an out of voltage part of the network")) {
                //we match line "Short circuit on node ABCDEP4_0 is not simulated : it is located in an out of voltage part of the network"
                if (logsShortCircuitNotSimulatedSummary == null) {
                    logsShortCircuitNotSimulatedSummary = new ReportWrapper();
                    newReporter.report(logsShortCircuitNotSimulatedSummary);
                    logsShortCircuitNotSimulatedSeverity = report.getValue(Report.REPORT_SEVERITY_KEY);
                }
                copyReportAsTrace(newReporter, report);
                logsShortCircuitNotSimulatedCount++;
            } else { //we keep this log as is
                newReporter.report(report);
            }
        }

        /* finalize computation of summaries */
        log.debug("Found {} lines in courcirc logs matching \"MYNODE : transient reactance too low ==> generator ignored\"", logsTransientReactanceTooLowCount);
        if (logsTransientReactanceTooLowSummary != null) {
            logsTransientReactanceTooLowSummary.setReport(new Report("transientReactanceTooLowSummary",
                    "${nb} node(s) with transient reactance too low ==> generator ignored",
                    Map.of(Report.REPORT_SEVERITY_KEY, ObjectUtils.defaultIfNull(logsTransientReactanceTooLowSeverity, TypedValue.WARN_SEVERITY),
                            "nb", new TypedValue(logsTransientReactanceTooLowCount, TypedValue.UNTYPED))));
        }
        log.debug("Found {} lines in courcirc logs matching \"MYNODE : transient reactance undefined ==> generator ignored\"", logsTransientReactanceUndefinedCount);
        if (logsTransientReactanceUndefinedSummary != null) {
            logsTransientReactanceUndefinedSummary.setReport(new Report("transientReactanceUndefinedSummary",
                    "${nb} node(s) with transient reactance undefined ==> generator ignored",
                    Map.of(Report.REPORT_SEVERITY_KEY, ObjectUtils.defaultIfNull(logsTransientReactanceUndefinedSeverity, TypedValue.WARN_SEVERITY),
                            "nb", new TypedValue(logsTransientReactanceUndefinedCount, TypedValue.UNTYPED))));
        }
        log.debug("Found {} lines in courcirc logs matching \"Simulating : short-circuit located on node MYNODE\"", logsSimulatingShortCircuitLocatedCount);
        if (logsSimulatingShortCircuitLocatedSummary != null) {
            logsSimulatingShortCircuitLocatedSummary.setReport(new Report("simulatingShortCircuitLocatedNodeSummary",
                    "Simulating: short-circuits located on ${nb} nodes",
                    Map.of(Report.REPORT_SEVERITY_KEY, ObjectUtils.defaultIfNull(logsSimulatingShortCircuitLocatedSeverity, TypedValue.INFO_SEVERITY),
                            "nb", new TypedValue(logsSimulatingShortCircuitLocatedCount, TypedValue.UNTYPED))));
        }
        log.debug("Found {} lines in courcirc logs matching \"Short circuit on node MYNODE is not simulated : it is located in an out of voltage part of the network\"", logsShortCircuitNotSimulatedCount);
        if (logsShortCircuitNotSimulatedSummary != null) {
            logsShortCircuitNotSimulatedSummary.setReport(new Report("shortCircuitNodeNotSimulatedOutOfVoltageSummary",
                    "Short circuit on ${nb} nodes is not simulated : they are in an out of voltage part of the network.",
                    Map.of(Report.REPORT_SEVERITY_KEY, ObjectUtils.defaultIfNull(logsShortCircuitNotSimulatedSeverity, TypedValue.WARN_SEVERITY),
                            "nb", new TypedValue(logsShortCircuitNotSimulatedCount, TypedValue.UNTYPED))));
        }

        return newReporter;
    }
}
