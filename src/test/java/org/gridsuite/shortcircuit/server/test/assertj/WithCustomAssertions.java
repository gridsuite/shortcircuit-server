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
import org.assertj.core.api.WithAssertions;
import org.assertj.core.util.CheckReturnValue;

@CheckReturnValue
public interface WithCustomAssertions extends WithAssertions {
    /**
     * Creates a new instance of <code>{@link ReporterAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    default ReporterAssert assertThat(Reporter actual) {
        return Assertions.assertThat(actual);
    }

    /**
     * Creates a new instance of <code>{@link ReporterModelAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    default AbstractReporterModelAssert<?> assertThat(ReporterModel actual) {
        return Assertions.assertThat(actual);
    }

    /**
     * Creates a new instance of <code>{@link ReportAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    default AbstractReportAssert<?> assertThat(Report actual) {
        return Assertions.assertThat(actual);
    }
}
