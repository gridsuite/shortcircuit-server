/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto.powsybl_private;

import java.util.List;

import org.gridsuite.shortcircuit.server.dto.FilterElements;

// DUPLICATED AND ADAPTED from private code should be removed
public class PowerElectronicsCluster extends AbstractPowerElectronicsData {

    List<FilterElements> filters; // ADDED

    protected PowerElectronicsCluster() {
        // Needed for deserialization
    }

    public PowerElectronicsCluster(double alpha, double u0, double usMin, double usMax, PowerElectronicsType type, List<FilterElements> filters, Boolean active) {
        super(alpha, u0, usMin, usMax, type, active);
        this.filters = filters;
    }

    public List<FilterElements> getFilters() {
        return filters;
    }
}
