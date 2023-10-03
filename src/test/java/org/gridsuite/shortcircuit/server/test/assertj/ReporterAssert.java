/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import org.assertj.core.api.InstanceOfAssertFactory;

/**
 * Assertion methods for {@code Reporter}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(Reporter)}</code>.
 */
public class ReporterAssert extends AbstractReporterAssert<ReporterAssert, Reporter> {
    public ReporterAssert(Reporter actual) {
        super(actual, ReporterAssert.class);
    }

    public AbstractReporterModelAssert<?> asInstanceOfReportModel() {
        return this.asInstanceOf(new InstanceOfAssertFactory<>(ReporterModel.class, Assertions::assertThat));
    }
}
