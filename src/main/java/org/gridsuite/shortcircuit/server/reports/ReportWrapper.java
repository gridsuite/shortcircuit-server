/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.TypedValue;
import lombok.Data;

import java.util.Map;

@Data
public class ReportWrapper extends Report { //we keep interface for polymorphisme
    private Report report;

    public ReportWrapper() {
        super("TODO", "Will be filled later", Map.of());
    }

    public ReportWrapper(final Report report) {
        this();
        this.report = report;
    }

    @Override
    public String getDefaultMessage() {
        return this.report == null ? null : this.report.getDefaultMessage();
    }

    @Override
    public String getReportKey() {
        return this.report == null ? null : this.report.getReportKey();
    }

    @Override
    public TypedValue getValue(String valueKey) {
        return this.report == null ? null : this.report.getValue(valueKey);
    }

    @Override
    public Map<String, TypedValue> getValues() {
        return this.report == null ? null : this.report.getValues();
    }
}
