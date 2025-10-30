/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import com.powsybl.shortcircuit.ShortCircuitParameters;

import lombok.Builder;
// import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * @since 1.7.0
 */
// @Jacksonized
@Builder
public record ShortCircuitParametersValues(
    String provider,
    ShortCircuitPredefinedConfiguration predefinedParameters,
    ShortCircuitParameters commonParameters,
    Map<String, String> specificParameters
) {
}
