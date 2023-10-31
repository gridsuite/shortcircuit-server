/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.entities.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@Slf4j
@Repository
public class ShortCircuitAnalysisResultRepository {
    private final GlobalStatusRepository globalStatusRepository;
    private final ResultRepository resultRepository;
    private final FaultResultRepository faultResultRepository;

    @Autowired
    public ShortCircuitAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
            ResultRepository resultRepository,
            FaultResultRepository faultResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
        this.faultResultRepository = faultResultRepository;
    }

    private static List<LimitViolationEmbeddable> extractLimitViolations(FaultResult faultResult) {
        return faultResult.getLimitViolations().stream()
            .map(limitViolation -> new LimitViolationEmbeddable(limitViolation.getSubjectId(),
                limitViolation.getLimitType(), limitViolation.getLimit(),
                limitViolation.getLimitName(), limitViolation.getValue()))
            .collect(Collectors.toList());
    }

    private static ShortCircuitAnalysisResultEntity toResultEntity(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitLimits> allShortCircuitLimits) {
        Set<FaultResultEntity> faultResults = result.getFaultResults()
                .stream()
                .map(faultResult -> {
                    if (faultResult instanceof FailedFaultResult failedFaultResult) {
                        return toGenericFaultResultEntity(failedFaultResult, null);
                    } else if (faultResult instanceof FortescueFaultResult fortescueFaultResult) {
                        return toFortescueFaultResultEntity(fortescueFaultResult, allShortCircuitLimits.get(faultResult.getFault().getId()));
                    } else if (faultResult instanceof MagnitudeFaultResult magnitudeFaultResult) {
                        return toMagnitudeFaultResultEntity(magnitudeFaultResult, allShortCircuitLimits.get(faultResult.getFault().getId()));
                    } else {
                        log.warn("Unknown FaultResult class: {}", faultResult.getClass());
                        return toGenericFaultResultEntity(faultResult, allShortCircuitLimits.get(faultResult.getFault().getId()));
                    }
                })
                .collect(Collectors.toSet());
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        return new ShortCircuitAnalysisResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), faultResults);
    }

    private static FaultResultEntity toGenericFaultResultEntity(final FaultResult faultResult, final ShortCircuitLimits shortCircuitLimits) {
        final Fault fault = faultResult.getFault();
        double ipMax = Double.NaN;
        double ipMin = Double.NaN;
        if (shortCircuitLimits != null) {
            ipMax = shortCircuitLimits.getIpMax();
            ipMin = shortCircuitLimits.getIpMin();
        }

        return new FaultResultEntity(
                new FaultEmbeddable(fault.getId(), fault.getElementId(), fault.getFaultType()),
                Double.NaN,
                faultResult.getShortCircuitPower(),
                extractLimitViolations(faultResult),
                null,
                ipMin, ipMax,
                null, null,
                Double.NaN, Double.NaN
        );
    }

    private static FaultResultEntity toMagnitudeFaultResultEntity(MagnitudeFaultResult faultResult, ShortCircuitLimits shortCircuitLimits) {
        FaultResultEntity entity = toGenericFaultResultEntity(faultResult, shortCircuitLimits);
        final double current = faultResult.getCurrent();
        entity.setCurrent(current);
        entity.setFeederResults(faultResult.getFeederResults().stream()
                .map(feederResult -> new FeederResultEmbeddable(feederResult.getConnectableId(),
                        ((MagnitudeFeederResult) feederResult).getCurrent(), null))
                .collect(Collectors.toList()));
        if (shortCircuitLimits != null) {
            entity.setDeltaCurrentIpMin(current - entity.getIpMin() / 1000.0);
            entity.setDeltaCurrentIpMax(current - entity.getIpMax() / 1000.0);
        }
        return entity;
    }

    private static FaultResultEntity toFortescueFaultResultEntity(FortescueFaultResult faultResult, ShortCircuitLimits shortCircuitLimits) {
        FaultResultEntity entity = toGenericFaultResultEntity(faultResult, shortCircuitLimits);
        entity.setFeederResults(faultResult.getFeederResults().stream()
                .map(feederResult -> {
                    final FortescueValue feederFortescueCurrent = ((FortescueFeederResult) feederResult).getCurrent();
                    final FortescueValue.ThreePhaseValue feederFortescueThreePhaseValue = feederFortescueCurrent.toThreePhaseValue();
                    return new FeederResultEmbeddable(feederResult.getConnectableId(), Double.NaN, new FortescueResultEmbeddable(
                            feederFortescueCurrent.getPositiveMagnitude(), feederFortescueCurrent.getZeroMagnitude(),
                            feederFortescueCurrent.getNegativeMagnitude(), feederFortescueCurrent.getPositiveAngle(),
                            feederFortescueCurrent.getZeroAngle(), feederFortescueCurrent.getNegativeAngle(),
                            feederFortescueThreePhaseValue.getMagnitudeA(), feederFortescueThreePhaseValue.getMagnitudeB(),
                            feederFortescueThreePhaseValue.getMagnitudeC(), feederFortescueThreePhaseValue.getAngleA(),
                            feederFortescueThreePhaseValue.getAngleB(), feederFortescueThreePhaseValue.getAngleC()));
                })
                .collect(Collectors.toList()));

        final FortescueValue current = faultResult.getCurrent();
        if (shortCircuitLimits != null) {
            entity.setDeltaCurrentIpMin(current.getPositiveMagnitude() - entity.getIpMin() / 1000.0);
            entity.setDeltaCurrentIpMax(current.getPositiveMagnitude() - entity.getIpMax() / 1000.0);
        }

        final FortescueValue.ThreePhaseValue currentThreePhaseValue = current.toThreePhaseValue();
        entity.setFortescueCurrent(new FortescueResultEmbeddable(current.getPositiveMagnitude(), current.getZeroMagnitude(), current.getNegativeMagnitude(), current.getPositiveAngle(), current.getZeroAngle(), current.getNegativeAngle(), currentThreePhaseValue.getMagnitudeA(), currentThreePhaseValue.getMagnitudeB(), currentThreePhaseValue.getMagnitudeC(), currentThreePhaseValue.getAngleA(), currentThreePhaseValue.getAngleB(), currentThreePhaseValue.getAngleC()));
        final FortescueValue voltage = faultResult.getVoltage();
        final FortescueValue.ThreePhaseValue voltageThreePhaseValue = voltage.toThreePhaseValue();
        entity.setFortescueVoltage(new FortescueResultEmbeddable(voltage.getPositiveMagnitude(), voltage.getZeroMagnitude(), voltage.getNegativeMagnitude(), voltage.getPositiveAngle(), voltage.getZeroAngle(), voltage.getNegativeAngle(), voltageThreePhaseValue.getMagnitudeA(), voltageThreePhaseValue.getMagnitudeB(), voltageThreePhaseValue.getMagnitudeC(), voltageThreePhaseValue.getAngleA(), voltageThreePhaseValue.getAngleB(), voltageThreePhaseValue.getAngleC()));

        return entity;
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
    public void insert(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitLimits> allCurrentLimits, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null && result.getFaultResults().stream().map(FaultResult::getStatus).noneMatch(FaultResult.Status.NO_SHORT_CIRCUIT_DATA::equals)) {
            resultRepository.save(toResultEntity(resultUuid, result, allCurrentLimits));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
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
    public Optional<Page<FaultResultEntity>> findFaultResultsPage(ShortCircuitAnalysisResultEntity result, Pageable pageable) {
        Objects.requireNonNull(result);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Optional<Page<FaultResultEntity>> faultResultsPage = faultResultRepository.findPagedByResult(result, pageable);
        if (faultResultsPage.isPresent()) {
            appendLimitViolationsAndFeederResults(faultResultsPage.get());
        }
        return faultResultsPage;
    }

    @Transactional(readOnly = true)
    public Optional<Page<FaultResultEntity>> findFaultResultsWithLimitViolationsPage(ShortCircuitAnalysisResultEntity result, Pageable pageable) {
        Objects.requireNonNull(result);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Optional<Page<FaultResultEntity>> faultResultsPage = faultResultRepository.findPagedByResultAndNbLimitViolationsGreaterThan(result, 0, pageable);
        if (faultResultsPage.isPresent()) {
            appendLimitViolationsAndFeederResults(faultResultsPage.get());
        }
        return faultResultsPage;
    }

    private void appendLimitViolationsAndFeederResults(Page<FaultResultEntity> pagedFaultResults) {
        // using the the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!pagedFaultResults.isEmpty()) {
            List<UUID> faultResultsUuids = pagedFaultResults.stream()
                    .map(FaultResultEntity::getFaultResultUuid)
                    .collect(Collectors.toList());
            faultResultRepository.findAllWithLimitViolationsByFaultResultUuidIn(faultResultsUuids);
            faultResultRepository.findAllWithFeederResultsByFaultResultUuidIn(faultResultsUuids);
        }
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
