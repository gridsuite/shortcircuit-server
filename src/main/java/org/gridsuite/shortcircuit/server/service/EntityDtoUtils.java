/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

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
}
