/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import lombok.Getter;

import java.util.Objects;

/**
 * @author David SARTORI <david.sartori_externe at rte-france.com>
 */
@Getter
public class ShortCircuitException extends RuntimeException {

    public enum Type {
        BUS_OUT_OF_VOLTAGE
    }

    private final Type type;

    public ShortCircuitException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public ShortCircuitException(Type type, String message) {
        super(message);
        this.type = type;
    }
}
