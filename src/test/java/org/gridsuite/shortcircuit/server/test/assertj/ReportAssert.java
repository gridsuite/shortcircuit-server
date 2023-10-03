/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;

/**
 * Assertion methods for {@code Report}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(Reporter)}</code>.
 */
public class ReportAssert extends AbstractReportAssert<ReportAssert> {
    public ReportAssert(Report actual) {
        super(actual, ReportAssert.class);
    }
}
