/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto.powsyblprivate;

import java.util.List;
import java.util.UUID;

// DUPLICATED AND ADAPTED from private code should be removed
/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
public class PowerElectronicsCluster extends AbstractPowerElectronicsData {

    public enum Type {
        GENERATOR,
        HVDC,
        UNKNOWN
    }

    List<UUID> filterUuids; // ADDED

    Type type;

    protected PowerElectronicsCluster() {
        // Needed for deserialization
    }

    public PowerElectronicsCluster(double alpha, double u0, double usMin, double usMax, Type type, List<UUID> filterUuids, Boolean active) {
        super(alpha, u0, usMin, usMax, active);
        this.filterUuids = filterUuids;
        this.type = type;
    }

    public List<UUID> getFilterUuids() {
        return filterUuids;
    }

    public Type getType() {
        return type;
    }

}
