/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
@NoArgsConstructor
public class ShortCircuitLimits {

    private static final String DEFAULT = "DEFAULT_VALUE";

    private String voltageLevelId;

    private double ipMin;

    private double ipMax;

    private Double deltaCurrentIpMin;

    private Double deltaCurrentIpMax;

    /**
     * For display
     * @param ipMin
     * @param ipMax
     * @param deltaCurrentIpMin
     * @param deltaCurrentIpMax
     */
    public ShortCircuitLimits(double ipMin, double ipMax, Double deltaCurrentIpMin, Double deltaCurrentIpMax) {
        this.voltageLevelId = DEFAULT;
        this.ipMin = ipMin;
        this.ipMax = ipMax;
        this.deltaCurrentIpMin = deltaCurrentIpMin;
        this.deltaCurrentIpMax = deltaCurrentIpMax;
    }

    /**
     * Calculate IN
     * @param voltageLevelId
     * @param ipMin
     * @param ipMax
     */
    public ShortCircuitLimits(String voltageLevelId, double ipMin, double ipMax) {
        this.voltageLevelId = voltageLevelId;
        this.ipMin = ipMin;
        this.ipMax = ipMax;
    }
}
