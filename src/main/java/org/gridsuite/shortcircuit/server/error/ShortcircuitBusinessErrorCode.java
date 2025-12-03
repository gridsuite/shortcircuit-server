/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.gridsuite.shortcircuit.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public enum ShortcircuitBusinessErrorCode implements BusinessErrorCode {
    BUS_OUT_OF_VOLTAGE("shortcircuit.busOutOfVoltage"),
    MISSING_EXTENSION_DATA("shortcircuit.missingExtensionData"),
    INCONSISTENT_VOLTAGE_LEVELS("shortcircuit.inconsistentVoltageLevels"),;

    private final String code;

    ShortcircuitBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
