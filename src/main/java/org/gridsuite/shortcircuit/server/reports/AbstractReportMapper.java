/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.report.*;
import jdk.javadoc.doclet.Reporter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;

import java.util.HashMap;
import java.util.Map;
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
     * @param reportNode reportNode to modify
     * @return the result
     *
     * @implNote currently support only some implementations of {@link Reporter}
     */
    public ReportNode processReporter(@NonNull final ReportNode reportNode, ShortCircuitRunContext runContext) {
        if (reportNode.getMessageKey() != null && reportNode.getMessageKey()
                .matches("^([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})")) {
            log.debug("ShortCircuitAnalysis root node found, will modify it!");
            return forUuidAtShortCircuitAnalysis(reportNode, runContext);
        } else {
            log.trace("Unrecognized ReportNode: {}", reportNode);
            return reportNode;
        }
    }

    public static ReportNode insertReportNode(ReportNode parent, ReportNode child) {
        ReportNodeAdder adder = parent.newReportNode().withMessageTemplate(child.getMessageKey(), child.getMessageTemplate());
        for (Map.Entry<String, TypedValue> valueEntry : child.getValues().entrySet()) {
            adder.withUntypedValue(valueEntry.getKey(), valueEntry.getValue().toString());
        }
        child.getValue(ReportConstants.SEVERITY_KEY).ifPresent(adder::withSeverity);
        ReportNode insertedChild = adder.add();
        if (child.getChildren() != null) {
            child.getChildren().forEach(grandChild -> insertReportNode(insertedChild, grandChild));
        }
        return insertedChild;
    }

    /**
     * Modify node with key {@code ********-****-****-****-************@ShortCircuitAnalysis} or {@code ShortCircuitAnalysis}
     *
     * @implNote we assume there will always be at least one modification
     */
    protected ReportNode forUuidAtShortCircuitAnalysis(@NonNull final ReportNode reportNode, ShortCircuitRunContext runContext) {
        ReportNodeBuilder builder = ReportNode.newRootReportNode()
                .withMessageTemplate(reportNode.getMessageKey(), reportNode.getMessageTemplate());
        reportNode.getValues().forEach((key, value) -> builder.withTypedValue(key, value.getValue().toString(), value.getType()));
        final ReportNode newReportNode = builder.build();

        reportNode.getChildren().forEach(child -> {
            if (child.getMessageKey() != null && child.getMessageKey().endsWith("ShortCircuitAnalysis")) {
                insertReportNode(newReportNode, forShortCircuitAnalysis(child, runContext));
            } else {
                insertReportNode(newReportNode, child);
            }
        });
        return newReportNode;
    }

    /**
     * Modify node with key {@code ShortCircuitAnalysis}
     */
    protected abstract ReportNode forShortCircuitAnalysis(@NonNull final ReportNode reportNode, ShortCircuitRunContext runContext);

    /**
     * Copy the reportNode, but with {@link TypedValue#TRACE_SEVERITY} severity
     * @param reportNode the {@link ReportNode reporter} to which add the modified {@link ReportNode}
     * @param child the report to copy with {@code TRACE} severity
     */
    static void copyReportAsTrace(@NonNull final ReportNode reportNode, @NonNull final ReportNode child) {
        final Map<String, TypedValue> values = new HashMap<>(child.getValues());
        values.put(ReportConstants.SEVERITY_KEY, TypedValue.TRACE_SEVERITY);
        ReportNodeAdder adder = reportNode.newReportNode().withMessageTemplate(child.getMessageKey(), child.getMessageTemplate());
        values.forEach((key, value) -> adder.withTypedValue(key, value.getValue().toString(), value.getType()));
        adder.add();
    }
}
