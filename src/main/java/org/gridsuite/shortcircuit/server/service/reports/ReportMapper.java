/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service.reports;

import com.powsybl.commons.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper to modify the logs//reports of ShortCircuit computations
 *
 * @implNote as the tree found during tests and debug seem to be relatively simple, a simple utility class will suffise
 */
public interface ReportMapper {
    /**
     * Entry point to this mapper to modify reports
     * @param reporter the root reporter to check and maybe modify
     * @return the new reporter instance if modified, else return the same reporter
     */
    Reporter mapReporter(@NotNull final Reporter reporter);

    /**
     * Name or ID of the mapper to distinct it, with the {@link #getVersion() version} from other mappers found
     * @return the name of this mapper
     */
    default String getName() {
        return this.getClass().getName();
    }

    /**
     * Version of this mapper, used to decide which to keep if multiple versions found
     * @return the version of this mapper
     */
    default int getVersion() {
        return 1;
    }
}
