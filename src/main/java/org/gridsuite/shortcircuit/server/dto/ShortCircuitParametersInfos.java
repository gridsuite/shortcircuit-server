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

import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;

import java.util.List;
import java.util.Map;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Builder
@Jacksonized
public record ShortCircuitParametersInfos(
    String provider,
    ShortCircuitPredefinedConfiguration predefinedParameters,
    ShortCircuitParameters commonParameters,
    Map<String, Map<String, String>> specificParametersPerProvider
) {
    @JsonProperty(access = Access.READ_ONLY)
    public List<VoltageRange> cei909VoltageRanges() {
        return ShortCircuitParametersConstants.CEI909_VOLTAGE_PROFILE;
    }

    public ShortCircuitParametersEntity toEntity() {
        return new ShortCircuitParametersEntity(this);
    }
}
