/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.NonNull;

import java.util.List;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */
public record ShortCircuitParametersInfos(
    ShortCircuitPredefinedConfiguration predefinedParameters,
    ShortCircuitParameters parameters,
    List<VoltageRange> cei909VoltageRanges
) {
    public ShortCircuitParametersInfos(@NonNull final ShortCircuitParametersInfos dto) {
        this(dto.predefinedParameters, dto.parameters == null ? null : copy(dto.parameters),
             dto.cei909VoltageRanges == null ? null : List.copyOf(dto.cei909VoltageRanges));
    }

    private static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
        return new ShortCircuitParameters()
                .setStudyType(shortCircuitParameters.getStudyType())
                .setMinVoltageDropProportionalThreshold(shortCircuitParameters.getMinVoltageDropProportionalThreshold())
                .setWithFeederResult(shortCircuitParameters.isWithFeederResult())
                .setWithLimitViolations(shortCircuitParameters.isWithLimitViolations())
                .setWithVoltageResult(shortCircuitParameters.isWithVoltageResult())
                .setWithFortescueResult(shortCircuitParameters.isWithFortescueResult())
                .setWithLoads(shortCircuitParameters.isWithLoads())
                .setWithShuntCompensators(shortCircuitParameters.isWithShuntCompensators())
                .setWithVSCConverterStations(shortCircuitParameters.isWithVSCConverterStations())
                .setWithNeutralPosition(shortCircuitParameters.isWithNeutralPosition())
                .setInitialVoltageProfileMode(shortCircuitParameters.getInitialVoltageProfileMode())
                .setVoltageRanges(shortCircuitParameters.getVoltageRanges());
    }
}
