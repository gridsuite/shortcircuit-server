/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.ws.commons.LogUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.gridsuite.shortcircuit.server.utils.FeederResultSpecifications;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitService.class);

    @Autowired
    NotificationService notificationService;

    private UuidGeneratorService uuidGeneratorService;

    private ShortCircuitAnalysisResultRepository resultRepository;

    private ObjectMapper objectMapper;

    public ShortCircuitService(NotificationService notificationService, UuidGeneratorService uuidGeneratorService, ShortCircuitAnalysisResultRepository resultRepository, ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(ShortCircuitRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), ShortCircuitAnalysisStatus.RUNNING.name());
        notificationService.sendRunMessage(new ShortCircuitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static ShortCircuitAnalysisResult fromEntity(ShortCircuitAnalysisResultEntity resultEntity, FaultResultsMode mode) {
        List<FaultResult> faultResults = new ArrayList<>();
        switch (mode) {
            case FULL:
                faultResults = resultEntity.getFaultResults().stream().map(fr -> fromEntity(fr)).collect(Collectors.toList());
                break;
            case WITH_LIMIT_VIOLATIONS:
                faultResults = resultEntity.getFaultResults().stream().filter(fr -> !fr.getLimitViolations().isEmpty()).map(fr -> fromEntity(fr)).collect(Collectors.toList());
                break;
            case NONE:
            default:
                break;
        }
        return new ShortCircuitAnalysisResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), faultResults);
    }

    private static FaultResult fromEntity(FaultResultEntity faultResultEntity) {
        Fault fault = fromEntity(faultResultEntity.getFault());
        double current = faultResultEntity.getCurrent();
        double positiveMagnitude = faultResultEntity.getPositiveMagnitude();
        double shortCircuitPower = faultResultEntity.getShortCircuitPower();
        ShortCircuitLimits shortCircuitLimits = new ShortCircuitLimits(faultResultEntity.getIpMin(), faultResultEntity.getIpMax(), faultResultEntity.getDeltaCurrentIpMin(), faultResultEntity.getDeltaCurrentIpMax());
        List<LimitViolation> limitViolations = faultResultEntity.getLimitViolations().stream().map(lv -> fromEntity(lv)).collect(Collectors.toList());
        List<FeederResult> feederResults = faultResultEntity.getFeederResults().stream().map(fr -> fromEntity(fr)).collect(Collectors.toList());
        return new FaultResult(fault, current, positiveMagnitude, shortCircuitPower, limitViolations, feederResults, shortCircuitLimits);
    }

    private static Fault fromEntity(FaultEmbeddable faultEmbeddable) {
        return new Fault(faultEmbeddable.getId(), faultEmbeddable.getElementId(), faultEmbeddable.getFaultType().name());
    }

    private static LimitViolation fromEntity(LimitViolationEmbeddable limitViolationEmbeddable) {
        return new LimitViolation(limitViolationEmbeddable.getSubjectId(), limitViolationEmbeddable.getLimitType().name(),
                limitViolationEmbeddable.getLimit(), limitViolationEmbeddable.getLimitName(), limitViolationEmbeddable.getValue());
    }

    private static FeederResult fromEntity(FeederResultEntity feederResultEntity) {
        return new FeederResult(feederResultEntity.getConnectableId(), feederResultEntity.getCurrent(), feederResultEntity.getPositiveMagnitude());
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

            ShortCircuitAnalysisResult res = fromEntity(sortedResult, mode);
            LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
            return res;
        }
        return null;
    }

    public ShortCircuitAnalysisPagedResults getPagedResults(UUID resultUuid, FaultResultsMode mode, ShortCircuitAnalysisType type, List<Filter> filters, Pageable pageable) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> resultEntity;
        // get without faultResults : FaultResultsM.NONE
        resultEntity = resultRepository.find(resultUuid);
        ShortCircuitAnalysisPagedResults results;
        if (type == ShortCircuitAnalysisType.ALL_BUSES) {
            results = getAllBusesResults(mode, pageable, resultEntity);
        } else {
            // mode is always FULL in ONE_BUS
            results = getOneBusResults(filters, pageable, resultEntity);
        }
        LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        String pageableStr = LogUtils.sanitizeParam(pageable.toString());
        LOGGER.info("pageable =  {}", pageableStr);
        return results;
    }

    @Nullable
    private ShortCircuitAnalysisPagedResultsAllBuses getAllBusesResults(FaultResultsMode mode, Pageable pageable, Optional<ShortCircuitAnalysisResultEntity> result) {
        if (result.isPresent()) {
            Optional<Page<FaultResultEntity>> faultResultEntitiesPage = Optional.empty();
            switch (mode) {
                case FULL:
                    faultResultEntitiesPage = resultRepository.findFaultResultsPage(result.get(), pageable);
                    break;
                case WITH_LIMIT_VIOLATIONS:
                    faultResultEntitiesPage = resultRepository.findFaultResultsWithLimitViolationsPage(result.get(), pageable);
                    break;
                case NONE:
                default:
                    break;
            }
            if (faultResultEntitiesPage.isPresent()) {
                Page<FaultResult> faultResultsPage = faultResultEntitiesPage.get().map(fr -> fromEntity(fr));
                return new ShortCircuitAnalysisPagedResultsAllBuses(faultResultsPage);
            }
        }
        return null;
    }

    @Nullable
    private ShortCircuitAnalysisPagedResultsOneBus getOneBusResults(List<Filter> filters, Pageable pageable, Optional<ShortCircuitAnalysisResultEntity> result) {
        if (result.isPresent()) {
            // if one bus, we have only one fault
            Optional<FaultResultEntity> faultResultEntity = resultRepository.findFirstFaultResult(result.get());
            if (faultResultEntity.isPresent()) {
                Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(faultResultEntity.get(), filters);
                Page<FeederResultEntity> feederResultEntitiesPage = resultRepository.findFeederResultsPage(specification, pageable);
                Page<FeederResult> feederResultsPage = feederResultEntitiesPage.map(fr -> fromEntity(fr));
                FaultResult faultResult = fromEntity(faultResultEntity.get());
                return new ShortCircuitAnalysisPagedResultsOneBus(faultResult, feederResultsPage);
            }
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
}
