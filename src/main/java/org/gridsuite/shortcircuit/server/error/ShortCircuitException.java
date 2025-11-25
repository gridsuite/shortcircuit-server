/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Objects;

/**
 * @author David SARTORI <david.sartori_externe at rte-france.com>
 */
@Getter
public class ShortCircuitException extends AbstractBusinessException {

    private final ShortcircuitBusinessErrorCode errorCode;

    public ShortCircuitException(ShortcircuitBusinessErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @NotNull
    @Override
    public ShortcircuitBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }
}
