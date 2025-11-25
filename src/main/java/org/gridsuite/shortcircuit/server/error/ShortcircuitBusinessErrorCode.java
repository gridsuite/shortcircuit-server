package org.gridsuite.shortcircuit.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

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
