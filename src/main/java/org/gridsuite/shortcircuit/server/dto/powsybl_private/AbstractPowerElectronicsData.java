/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto.powsybl_private;

// DUPLICATED AND ADAPTED from private code should be removed
/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
public abstract class AbstractPowerElectronicsData {

    protected AbstractPowerElectronicsData() {
    }

    public enum PowerElectronicsType {
        WIND,
        SOLAR,
        HVDC
    }

    private double alpha;
    private double u0;
    private double usMin;
    private double usMax;
    PowerElectronicsType type;
    // ADDED
    Boolean active = true;

    protected AbstractPowerElectronicsData(double alpha, double u0, double usMin, double usMax, PowerElectronicsType type, Boolean active) {
        this.alpha = alpha;
        this.u0 = u0;
        this.usMin = usMin;
        this.usMax = usMax;
        this.type = type;
        this.active = active;
    }

    public double getAlpha() {
        return alpha;
    }

    public double getU0() {
        return u0;
    }

    public double getUsMin() {
        return usMin;
    }

    public double getUsMax() {
        return usMax;
    }

    public PowerElectronicsType getType() {
        return type;
    }

    // ADDED
    public Boolean isActive() {
        return active;
    }
}
