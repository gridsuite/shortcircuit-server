/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;

import java.util.UUID;

/**
 * This class manages how to postprocess reports of the short-circuit server to reduce the number of reports
 * by aggregating them.
 * <br/>
 * The start tree structure returned by short-circuit is:
 * <pre>
 * 00000000-0000-0000-0000-000000000000@ShortCircuitAnalysis  or  ShortCircuitAnalysis
 * \-- ShortCircuitAnalysis -> "ShortCircuitAnalysis (${providerToUse})"
 *     +-- [...]
 *     \-- [...]
 * </pre>
 *
 * @implNote as the firsts nodes are created by ShortCircuit, we know them, and are common for other implementations
 *
 * @see org.gridsuite.shortcircuit.server.service.ShortCircuitWorkerService#run(ShortCircuitRunContext, UUID)
 */
@Slf4j
public abstract class AbstractReportMapper {
    /**
     * Will try to modify the reporter
     * @param reporter the reporter to modify
     * @return the result
     *
     * @implNote currently support only some implementations of {@link Reporter}
     */
    public Reporter processReporter(@NonNull final Reporter reporter) {
        if (reporter instanceof ReporterModel reporterModel && reporterModel.getTaskKey().matches("^([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}@)?ShortCircuitAnalysis$")) {
            log.debug("ShortCircuitAnalysis root node found, will modify it!");
            return forUuidAtShortCircuitAnalysis(reporterModel);
        } else {
            log.trace("Unrecognized Reporter: {}", reporter);
            return reporter;
        }
    }

    /**
     * Modify node with key {@code ********-****-****-****-************@ShortCircuitAnalysis} or {@code ShortCircuitAnalysis}
     *
     * @implNote we assume there will always be at least one modification
     */
    protected Reporter forUuidAtShortCircuitAnalysis(@NonNull final ReporterModel reporterModel) {
        final ReporterModel newReporter = new ReporterModel(reporterModel.getTaskKey(), reporterModel.getDefaultName(), reporterModel.getTaskValues());
        reporterModel.getReports().forEach(newReporter::report);
        reporterModel.getSubReporters().forEach(reporter -> {
            if ("ShortCircuitAnalysis".equals(reporter.getTaskKey())) {
                newReporter.addSubReporter(forShortCircuitAnalysis(reporter));
            } else {
                newReporter.addSubReporter(reporter);
            }
        });
        return newReporter;
    }

    /**
     * Modify node with key {@code ShortCircuitAnalysis}
     */
    protected abstract ReporterModel forShortCircuitAnalysis(@NonNull final ReporterModel reporterModel);
}
