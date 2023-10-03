/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import org.assertj.core.util.CheckReturnValue;

public class Assertions extends org.assertj.core.api.Assertions {
    /**
     * Creates a new instance of <code>{@link ReporterAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @CheckReturnValue
    public static ReporterAssert assertThat(Reporter actual) {
        return new ReporterAssert(actual);
    }

    /**
     * Creates a new instance of <code>{@link ReporterAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @CheckReturnValue
    public static AbstractReporterModelAssert<?> assertThat(ReporterModel actual) {
        return new ReporterModelAssert(actual);
    }

    /**
     * Creates a new instance of <code>{@link ReportAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @CheckReturnValue
    public static AbstractReportAssert<?> assertThat(Report actual) {
        return new ReportAssert(actual);
    }
}
