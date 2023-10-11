/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.reports;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper to modify the logs/reports of ShortCircuit computations
 *
 * @see Reporter
 * @see Report
 */
public interface ReportMapper {
    /**
     * Entry point to this mapper to modify reports
     * @param reporter the root reporter to check and maybe modify
     * @return the new reporter instance if modified, else return the same reporter
     */
    Reporter processReporter(@NotNull final Reporter reporter);
}
