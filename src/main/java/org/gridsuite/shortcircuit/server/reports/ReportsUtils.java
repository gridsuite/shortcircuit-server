/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ReportsUtils {
    private ReportsUtils() {
        throw new IllegalCallerException("Utility class");
    }

    /**
     * Copy the report, but with {@link TypedValue#TRACE_SEVERITY} severity
     * @param reporterModel the {@link ReporterModel reporter} to wich add the modified {@link Report}
     * @param report the report to copy with {@code TRACE} severity
     */
    public static void copyReportAsTrace(@NonNull final ReporterModel reporterModel, @NonNull final Report report) {
        //TODO use .equals() when implemented in TypedValue
        if (equalsTypedValue(TypedValue.TRACE_SEVERITY, report.getValue(Report.REPORT_SEVERITY_KEY))) {
            reporterModel.report(report); //no change needed
        } else {
            final Map<String, TypedValue> values = new HashMap<>(report.getValues());
            values.put(Report.REPORT_SEVERITY_KEY, TypedValue.TRACE_SEVERITY);
            reporterModel.report(new Report(report.getReportKey(), report.getDefaultMessage(), values));
        }
    }

    /**
     * Because {@link TypedValue#equals(Object)} isn't override, we must test it ourselves.
     * @param obj1 first object to test
     * @param obj2 second object to test
     * @return {@code true} if equals, else {@code false}
     */
    static boolean equalsTypedValue(final TypedValue obj1, final TypedValue obj2) {
        if (obj1 == obj2) {
            return true;
        } else if (obj1 != null && obj2 != null) {
            return Objects.equals(obj1.getType(), obj2.getType()) && Objects.equals(obj1.getValue(), obj2.getValue());
        } else {
            return false;
        }
    }
}
