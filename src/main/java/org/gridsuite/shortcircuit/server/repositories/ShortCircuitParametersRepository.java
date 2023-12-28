/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import lombok.NonNull;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShortCircuitParametersRepository extends JpaRepository<ShortCircuitParametersEntity, UUID> {
    default ShortCircuitParametersEntity getByIdOrDefault(final UUID id) {
        return findById(id).orElseGet(() -> this.save(convert(getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)));
    }

    private static ShortCircuitParameters getDefaultShortCircuitParameters() {
        return new ShortCircuitParameters()
                .setStudyType(StudyType.TRANSIENT)
                .setMinVoltageDropProportionalThreshold(20)
                .setWithFeederResult(true)
                .setWithLimitViolations(true)
                .setWithVoltageResult(false)
                .setWithFortescueResult(false)
                .setWithLoads(false)
                .setWithShuntCompensators(false)
                .setWithVSCConverterStations(true)
                .setWithNeutralPosition(true)
                .setInitialVoltageProfileMode(InitialVoltageProfileMode.NOMINAL)
                // the voltageRanges is not taken into account when initialVoltageProfileMode=NOMINAL
                .setVoltageRanges(null);
    }

    private static ShortCircuitParametersEntity convert(@NonNull final ShortCircuitParameters parameters, final ShortCircuitPredefinedConfiguration shortCircuitPredefinedConfiguration) {
        return new ShortCircuitParametersEntity(parameters.isWithLimitViolations(),
                parameters.isWithVoltageResult(),
                parameters.isWithFortescueResult(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold(),
                parameters.isWithLoads(),
                parameters.isWithShuntCompensators(),
                parameters.isWithVSCConverterStations(),
                parameters.isWithNeutralPosition(),
                parameters.getInitialVoltageProfileMode(),
                shortCircuitPredefinedConfiguration);
    }
}
