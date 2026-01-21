/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import com.powsybl.shortcircuit.ShortCircuitParameters;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortCircuitParametersValues {

    private String provider;
    private ShortCircuitPredefinedConfiguration predefinedParameters;
    private ShortCircuitParameters commonParameters;
    // Mutable map that can hold structured values (JsonNode / objects) to avoid double-quoting
    @Builder.Default
    private Map<String, Object> specificParameters = new HashMap<>();
}
