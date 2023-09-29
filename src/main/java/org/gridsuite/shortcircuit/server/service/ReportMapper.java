package org.gridsuite.shortcircuit.server.service;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class manage how to modify logs of a computation results.
 * <br/>
 * The tree structure found during debug is this one :
 * <pre>
 * 00000000-0000-0000-0000-000000000000@ShortCircuitAnalysis
 * \-- ShortCircuitAnalysis -> "ShortCircuitAnalysis (${providerToUse})"
 *     +-- generatorConversion -> "Conversion of generators"
 *     +-- batteryConversion -> "Conversion of the batteries"
 *     +-- branchConversion -> "Conversion of branches"
 *     |   +-- lineConversion -> "Conversion of lines"
 *     |   +-- tieLineConversion -> "Conversion of tie lines"
 *     |   +-- twoWindingsTransformerConversion -> "Conversion of two windings transformers"
 *     |   \-- threeWindingsTransformerConversion -> "Conversion of three windings transformers"
 *     +-- danglinglinesConversion -> "Conversion of the dangling lines"
 *     \-- courcirc -> "Logs generated by Courcirc simulator"
 * </pre>
 *
 * @implNote as the tree found during tests and debug seem to be relatively simple, a simple utility class will suffise
 *
 * @see com.powsybl.commons.reporter.Reporter
 * @see com.powsybl.commons.reporter.ReporterModel
 * @see com.powsybl.commons.reporter.Report
 */
@SuppressWarnings("SwitchStatementWithTooFewBranches") //for future additions
@Slf4j
@Component
public class ReportMapper {
    /**
     * Will try to modify the reporter
     * @param reporter the reporter to modify
     * @return the result
     *
     * @apiNote is limited to some implementations of {@link Reporter}
     */
    public Reporter modifyReporter(@NonNull final Reporter reporter) {
        if(reporter instanceof ReporterModel reporterModel) {
            log.trace("ReportModel found");
            return modifyReporterModel(reporterModel);
        } else {
            log.trace("Unrecognized Reporter of type {}", reporter.getClass().getSimpleName());
            return reporter;
        }
    }

    /**
     * Starting point, determine the root type
     */
    protected Reporter modifyReporterModel(@NonNull final ReporterModel reporterModel) {
        if(reporterModel.getTaskKey().matches("^\\d{8}-\\d{4}-\\d{4}-\\d{4}-\\d{12}@ShortCircuitAnalysis$")) {
            log.debug("ShortCircuitAnalysis root node found, will modify it!");
            return forUuidAtShortCircuitAnalysis(reporterModel);
        } else {
            log.debug("No treatment for root node {}", reporterModel.getTaskKey());
            return reporterModel;
        }
    }

    /**
     * Modify node with key {@code ********-****-****-****-************@ShortCircuitAnalysis}
     *
     * @implNote we assume there will always be at least one modification
     */
    protected Reporter forUuidAtShortCircuitAnalysis(@NonNull final ReporterModel reporterModel) {
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getReports().forEach(newReporter::report);
        reporterModel.getSubReporters().forEach(reporter -> newReporter.addSubReporter(switch (reporter.getTaskKey()) {
            case "ShortCircuitAnalysis" -> forShortCircuitAnalysis(reporter);
            default -> reporter;
        }));
        return newReporter;
    }

    /**
     * Modify node with key {@code ShortCircuitAnalysis}
     *
     * @implNote we assume there will always be at least one modification
     */
    protected ReporterModel forShortCircuitAnalysis(@NonNull final ReporterModel reporterModel) {
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getReports().forEach(newReporter::report);
        reporterModel.getSubReporters().forEach(reporter -> newReporter.addSubReporter(switch (reporter.getTaskKey()) {
            case "generatorConversion" -> forGeneratorConversion(reporter);
            case "courcirc" -> forCourcirc(reporter);
            default -> reporter;
        }));
        return newReporter;
    }

    /**
     * Modify node with key {@code generatorConversion}
     */
    protected ReporterModel forGeneratorConversion(@NonNull final ReporterModel reporterModel) {
        return reporterModel;
    }

    /**
     * Modify node with key {@code courcirc}
     *
     * @apiNote the main goal here is to concat specific log lines with a huge number of repetitions
     * @implNote courcirc logs don't seem to use enumerate keys (REC_0,REC_1,...) neither values, so we need to test the message
     */
    protected ReporterModel forCourcirc(@NonNull final ReporterModel reporterModel) {
        log.trace("courcirc logs detected, will analyse them...");
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getSubReporters().forEach(newReporter::addSubReporter);
        /* preparing */
        final Pattern patternTransientReactanceTooLow = Pattern.compile("^([^ ]+) +: transient reactance too low ==> generator ignored$", Pattern.CASE_INSENSITIVE);
        final List<String> logsTransientReactanceTooLow = new ArrayList<>(newReporter.getReports().size());
        /* analyze and compute logs in one pass */
        for (final Report report : reporterModel.getReports()) { //we modify logs conditionally here
            final Matcher matcherTransientReactanceTooLow = patternTransientReactanceTooLow.matcher(report.getDefaultMessage());
            if(matcherTransientReactanceTooLow.matches()) { //we match line "X.ABCDEF1 : transient reactance too low ==> generator ignored"
                logsTransientReactanceTooLow.add(matcherTransientReactanceTooLow.group(1));
            } else { //we keep this log as it
                newReporter.report(report);
            }
        }
        /* finalize computation */
        log.debug("Found {} lines in courcirc logs matching \"MYNODE : transient reactance too low ==> generator ignored\"", logsTransientReactanceTooLow.size());
        newReporter.report("TransientReactanceTooLow", "${nb} node(s) with transient reactance too low ==> generator ignored\n${nodes}",
                           Map.of("nb", new TypedValue(logsTransientReactanceTooLow.size(), TypedValue.UNTYPED),
                                  "nodes", new TypedValue(String.join(", ", logsTransientReactanceTooLow), TypedValue.UNTYPED)));
        return newReporter;
    }
}
