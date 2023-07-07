/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitCurrentLimits;
import org.gridsuite.shortcircuit.server.entities.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public class ShortCircuitAnalysisResultRepository {

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;

    private FaultResultRepository faultResultRepository;

    public ShortCircuitAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                                ResultRepository resultRepository,
                                                FaultResultRepository faultResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
        this.faultResultRepository = faultResultRepository;
    }

    private static ShortCircuitAnalysisResultEntity toResultEntity(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitCurrentLimits> allCurrentLimits) {
        Set<FaultResultEntity> faultResults = result.getFaultResults().stream().map(faultResult -> toFaultResultEntityWithCurrentLimits(faultResult, allCurrentLimits.get(faultResult.getFault().getId()))).collect(Collectors.toSet());
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        return new ShortCircuitAnalysisResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), faultResults);
    }

    private static FaultResultEntity toFaultResultEntityWithCurrentLimits(FaultResult faultResult, ShortCircuitCurrentLimits currentLimits) {
        FaultResultEntity faultResultEntity = toFaultResultEntity(faultResult);
        faultResultEntity.setIpMax(currentLimits.getIpMax());
        faultResultEntity.setIpMin(currentLimits.getIpMin());
        return faultResultEntity;
    }

    private static FaultResultEntity toFaultResultEntity(FaultResult faultResult) {
        Fault fault = faultResult.getFault();
        double current = ((MagnitudeFaultResult) faultResult).getCurrent();
        double shortCircuitPower = faultResult.getShortCircuitPower();

        FaultEmbeddable faultEmbedded = new FaultEmbeddable(fault.getId(), fault.getElementId(), fault.getFaultType());

        List<LimitViolationEmbeddable> limitViolations = faultResult.getLimitViolations().stream().map(limitViolation ->
                new LimitViolationEmbeddable(limitViolation.getSubjectId(), limitViolation.getLimitType(), limitViolation.getLimit(),
                        limitViolation.getLimitName(), limitViolation.getValue()))
                .collect(Collectors.toList());

        List<FeederResultEmbeddable> feederResults = faultResult.getFeederResults().stream().map(feederResult ->
                new FeederResultEmbeddable(feederResult.getConnectableId(), ((MagnitudeFeederResult) feederResult).getCurrent()))
                .collect(Collectors.toList());

        return new FaultResultEntity(null, faultEmbedded, current, shortCircuitPower, limitViolations, feederResults, Double.NaN, Double.NaN);
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitCurrentLimits> allCurrentLimits) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result, allCurrentLimits));
        }
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitAnalysisResultEntity> find(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findByResultUuid(resultUuid);
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitAnalysisResultEntity> findFullResults(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.findAllWithLimitViolationsByResultUuid(resultUuid);
        if (!result.isPresent()) {
            return result;
        }
        // using the the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!result.get().getFaultResults().isEmpty()) {
            resultRepository.findAllWithFeederResultsByResultUuid(resultUuid);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitAnalysisResultEntity> findResultsWithLimitViolations(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.findAllWithLimitViolationsByResultUuid(resultUuid);
        if (!result.isPresent()) {
            return result;
        }
        List<UUID> faultResultsUuidWithLimitViolations = result.get().getFaultResults().stream()
                                                            .filter(fr -> !fr.getLimitViolations().isEmpty())
                                                            .map(FaultResultEntity::getFaultResultUuid)
                                                            .collect(Collectors.toList());
        // using the the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!result.get().getFaultResults().isEmpty()) {
            faultResultRepository.findAllWithFeederResultsByFaultResultUuidIn(faultResultsUuidWithLimitViolations);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            return null;
        }
    }
}
