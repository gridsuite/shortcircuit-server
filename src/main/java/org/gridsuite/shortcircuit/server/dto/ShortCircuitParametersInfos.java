/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;

import java.util.List;

/**
 * @since 1.7.0
 */
@Builder
@Jacksonized
public record ShortCircuitParametersInfos(
    ShortCircuitPredefinedConfiguration predefinedParameters,
    ShortCircuitParameters parameters
) {
    @JsonProperty(access = Access.READ_ONLY)
    public List<VoltageRange> cei909VoltageRanges() {
        return ShortCircuitService.CEI909_VOLTAGE_PROFILE;
    }
}
