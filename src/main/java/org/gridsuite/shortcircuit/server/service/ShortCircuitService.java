/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.ws.commons.LogUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RequiredArgsConstructor
@Service
public class ShortCircuitService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitService.class);

    @NonNull private final NotificationService notificationService;
    @NonNull private final UuidGeneratorService uuidGeneratorService;
    @NonNull private final ShortCircuitAnalysisResultRepository resultRepository;
    @NonNull private final ObjectMapper objectMapper;

    public UUID runAndSaveResult(ShortCircuitRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), ShortCircuitAnalysisStatus.RUNNING.name());
        notificationService.sendRunMessage(new ShortCircuitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static ShortCircuitAnalysisResultEntity sortByElementId(ShortCircuitAnalysisResultEntity result) {
        result.setFaultResults(result.getFaultResults().stream()
                .sorted(Comparator.comparing(fr -> fr.getFault().getElementId()))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return result;
    }

    public ShortCircuitAnalysisResult getResult(UUID resultUuid, FaultResultsMode mode) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result;
        switch (mode) {
            case BASIC:
                result = resultRepository.findWithFaultResults(resultUuid);
                break;
            case FULL:
                result = resultRepository.findFullResults(resultUuid);
                break;
            case WITH_LIMIT_VIOLATIONS:
                result = resultRepository.findResultsWithLimitViolations(resultUuid);
                break;
            case NONE:
            default:
                result = resultRepository.find(resultUuid);
                break;
        }
        if (result.isPresent()) {
            ShortCircuitAnalysisResultEntity sortedResult = sortByElementId(result.get());

            ShortCircuitAnalysisResult res = EntityDtoUtils.convert(sortedResult, mode);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
            }
            return res;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FaultResult> getFaultResultsPage(UUID resultUuid, FaultResultsMode mode, List<ResourceFilter> resourceFilters, Pageable pageable) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result;
        // get without faultResults : FaultResultsM.NONE
        result = resultRepository.find(resultUuid);
        if (result.isPresent()) {
            Page<FaultResultEntity> faultResultEntitiesPage = Page.empty();
            switch (mode) {
                case BASIC, FULL:
                    faultResultEntitiesPage = resultRepository.findFaultResultsPage(result.get(), resourceFilters, pageable, mode);
                    break;
                case WITH_LIMIT_VIOLATIONS:
                    faultResultEntitiesPage = resultRepository.findFaultResultsWithLimitViolationsPage(result.get(), resourceFilters, pageable);
                    break;
                case NONE:
                default:
                    break;
            }
            Page<FaultResult> faultResultsPage = faultResultEntitiesPage.map(fr -> EntityDtoUtils.convert(fr, mode));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return faultResultsPage;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FeederResult> getFeederResultsPage(UUID resultUuid, List<ResourceFilter> resourceFilters, Pageable pageable) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.find(resultUuid);
        if (result.isPresent()) {
            Page<FeederResultEntity> feederResultEntitiesPage = resultRepository.findFeederResultsPage(result.get(), resourceFilters, pageable);
            Page<FeederResult> feederResultsPage = feederResultEntitiesPage.map(EntityDtoUtils::convert);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return feederResultsPage;
        }
        return null;
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        resultRepository.insertStatus(resultUuids, status);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new ShortCircuitCancelContext(resultUuid, receiver).toMessage());
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
