/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.ReporterModel;

/**
 * Assertion methods for {@code Reporter}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(ReporterModel)}</code>.
 */
public class ReporterModelAssert extends AbstractReporterModelAssert<ReporterModelAssert> {
    public ReporterModelAssert(ReporterModel actual) {
        super(actual, ReporterModelAssert.class);
    }
}
