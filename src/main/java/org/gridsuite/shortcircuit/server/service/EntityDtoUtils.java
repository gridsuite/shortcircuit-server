/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.NonNull;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.*;

import java.util.Collections;
import java.util.List;

/**
 * Mapper class between {@link org.gridsuite.shortcircuit.server.entities entities} and {@link org.gridsuite.shortcircuit.server.dto DTOs}.
 */
final class EntityDtoUtils {
    private EntityDtoUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final List<VoltageRange> CEI909_VOLTAGE_PROFILE = List.of(
            new VoltageRange(10.0, 199.99, 1.1),
            new VoltageRange(200.0, 299.99, 1.09),
            new VoltageRange(300.0, 500.0, 1.05)
    );

    static ShortCircuitAnalysisResult convert(ShortCircuitAnalysisResultEntity resultEntity, FaultResultsMode mode) {
        final List<FaultResult> faultResults = switch (mode) {
            case BASIC, FULL -> resultEntity.getFaultResults().stream().map(fr -> convert(fr, mode)).toList();
            case WITH_LIMIT_VIOLATIONS -> resultEntity.getFaultResults().stream().filter(fr -> !fr.getLimitViolations().isEmpty()).map(fr -> convert(fr, mode)).toList();
            case NONE -> Collections.emptyList();
        };
        return new ShortCircuitAnalysisResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), faultResults);
    }

    static FaultResult convert(@NonNull final FaultResultEntity faultResultEntity, @NonNull final FaultResultsMode mode) {
        List<LimitViolation> limitViolations;
        List<FeederResult> feederResults;
        if (mode != FaultResultsMode.BASIC) {
            // if we enter here, by calling the getters, the limit violations and feeder results will be loaded even if we don't want to in some mode
            limitViolations = faultResultEntity.getLimitViolations().stream().map(EntityDtoUtils::convert).toList();
            feederResults = faultResultEntity.getFeederResults().stream().map(EntityDtoUtils::convert).toList();
        } else {
            limitViolations = Collections.emptyList();
            feederResults = Collections.emptyList();
        }
        return new FaultResult(
                convert(faultResultEntity.getFault()),
                faultResultEntity.getCurrent(),
                faultResultEntity.getPositiveMagnitude(),
                faultResultEntity.getShortCircuitPower(),
                limitViolations,
                feederResults,
                new ShortCircuitLimits(faultResultEntity.getIpMin(), faultResultEntity.getIpMax(), faultResultEntity.getDeltaCurrentIpMin(), faultResultEntity.getDeltaCurrentIpMax())
        );
    }

    static Fault convert(@NonNull final FaultEmbeddable faultEmbeddable) {
        return new Fault(faultEmbeddable.getId(), faultEmbeddable.getElementId(), faultEmbeddable.getFaultType().name());
    }

    static LimitViolation convert(@NonNull final LimitViolationEmbeddable limitViolationEmbeddable) {
        return new LimitViolation(
            limitViolationEmbeddable.getSubjectId(),
            limitViolationEmbeddable.getLimitType().name(),
            limitViolationEmbeddable.getLimit(),
            limitViolationEmbeddable.getLimitName(),
            limitViolationEmbeddable.getValue()
        );
    }

    static FeederResult convert(@NonNull final FeederResultEntity feederResultEntity) {
        return new FeederResult(feederResultEntity.getConnectableId(), feederResultEntity.getCurrent(), feederResultEntity.getPositiveMagnitude());
    }

    static ShortCircuitParameters convert(@NonNull final ShortCircuitParametersEntity entity) {
        return new ShortCircuitParameters()
                .setStudyType(entity.getStudyType())
                .setMinVoltageDropProportionalThreshold(entity.getMinVoltageDropProportionalThreshold())
                .setWithFeederResult(entity.isWithFeederResult())
                .setWithLimitViolations(entity.isWithLimitViolations())
                .setWithVoltageResult(entity.isWithVoltageResult())
                .setWithFortescueResult(entity.isWithFortescueResult())
                .setWithLoads(entity.isWithLoads())
                .setWithShuntCompensators(entity.isWithShuntCompensators())
                .setWithVSCConverterStations(entity.isWithVscConverterStations())
                .setWithNeutralPosition(entity.isWithNeutralPosition())
                .setInitialVoltageProfileMode(entity.getInitialVoltageProfileMode())
                .setVoltageRanges(InitialVoltageProfileMode.CONFIGURED.equals(entity.getInitialVoltageProfileMode()) ? CEI909_VOLTAGE_PROFILE : null);
    }

    static ShortCircuitParametersInfos convertInfos(final @NonNull ShortCircuitParametersEntity entity) {
        return new ShortCircuitParametersInfos(entity.getPredefinedParameters(), convert(entity), CEI909_VOLTAGE_PROFILE);
    }
}
