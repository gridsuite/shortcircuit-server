/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import org.gridsuite.shortcircuit.server.dto.FaultResultsMode;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.utils.FaultResultSpecificationBuilder;
import org.gridsuite.shortcircuit.server.utils.FeederResultSpecificationBuilder;
import org.gridsuite.shortcircuit.server.utils.ShortcircuitUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    private FeederResultRepository feederResultRepository;

    public ShortCircuitAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                                ResultRepository resultRepository,
                                                FaultResultRepository faultResultRepository,
                                                FeederResultRepository feederResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
        this.faultResultRepository = faultResultRepository;
        this.feederResultRepository = feederResultRepository;
    }

    private static List<LimitViolationEmbeddable> extractLimitViolations(FaultResult faultResult) {
        return faultResult.getLimitViolations().stream()
            .map(limitViolation -> new LimitViolationEmbeddable(limitViolation.getSubjectId(),
                limitViolation.getLimitType(), limitViolation.getLimit(),
                limitViolation.getLimitName(), limitViolation.getValue()))
            .collect(Collectors.toList());
    }

    public static ShortCircuitAnalysisResultEntity toResultEntity(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitLimits> allShortCircuitLimits, boolean isWithFortescueResult) {
        Set<FaultResultEntity> faultResults = result.getFaultResults().stream().map(faultResult -> isWithFortescueResult ? toFortescueFaultResultEntity(faultResult, allShortCircuitLimits.get(faultResult.getFault().getId())) : toMagnitudeFaultResultEntity(faultResult, allShortCircuitLimits.get(faultResult.getFault().getId()))).collect(Collectors.toSet());
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        return new ShortCircuitAnalysisResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), faultResults);
    }

    private static FaultResultEntity toMagnitudeFaultResultEntity(FaultResult faultResult, ShortCircuitLimits shortCircuitLimits) {
        Fault fault = faultResult.getFault();
        double current = ((MagnitudeFaultResult) faultResult).getCurrent();
        double shortCircuitPower = faultResult.getShortCircuitPower();
        FaultEmbeddable faultEmbedded = new FaultEmbeddable(fault.getId(), fault.getElementId(), fault.getFaultType());
        List<LimitViolationEmbeddable> limitViolations = extractLimitViolations(faultResult);
        List<FeederResultEntity> feederResults = faultResult.getFeederResults().stream()
                .map(feederResult -> new FeederResultEntity(null, feederResult.getConnectableId(),
                        ((MagnitudeFeederResult) feederResult).getCurrent(), null))
                .collect(Collectors.toList());

        double ipMax = Double.NaN;
        double ipMin = Double.NaN;
        double deltaCurrentIpMin = Double.NaN;
        double deltaCurrentIpMax = Double.NaN;
        if (shortCircuitLimits != null) {
            ipMax = shortCircuitLimits.getIpMax();
            ipMin = shortCircuitLimits.getIpMin();
            deltaCurrentIpMin = current - ipMin / 1000;
            deltaCurrentIpMax = current - ipMax / 1000;
        }
        return new FaultResultEntity(faultEmbedded, current, shortCircuitPower, limitViolations, feederResults, ipMin, ipMax, null, null, deltaCurrentIpMin, deltaCurrentIpMax);
    }

    private static FaultResultEntity toFortescueFaultResultEntity(FaultResult faultResult, ShortCircuitLimits shortCircuitLimits) {
        Fault fault = faultResult.getFault();
        double shortCircuitPower = faultResult.getShortCircuitPower();
        FaultEmbeddable faultEmbedded = new FaultEmbeddable(fault.getId(), fault.getElementId(), fault.getFaultType());
        List<LimitViolationEmbeddable> limitViolations = extractLimitViolations(faultResult);
        List<FeederResultEntity> feederResults = faultResult.getFeederResults().stream()
            .map(feederResult -> {
                FortescueValue feederFortescueCurrent = ((FortescueFeederResult) feederResult).getCurrent();
                FortescueValue.ThreePhaseValue feederFortescueThreePhaseValue = ShortcircuitUtils.toThreePhaseValue(feederFortescueCurrent);
                return new FeederResultEntity(null, feederResult.getConnectableId(),
                    Double.NaN, new FortescueResultEmbeddable(feederFortescueCurrent.getPositiveMagnitude(), feederFortescueCurrent.getZeroMagnitude(), feederFortescueCurrent.getNegativeMagnitude(), feederFortescueCurrent.getPositiveAngle(), feederFortescueCurrent.getZeroAngle(), feederFortescueCurrent.getNegativeAngle(), feederFortescueThreePhaseValue.getMagnitudeA(), feederFortescueThreePhaseValue.getMagnitudeB(), feederFortescueThreePhaseValue.getMagnitudeC(), feederFortescueThreePhaseValue.getAngleA(), feederFortescueThreePhaseValue.getAngleB(), feederFortescueThreePhaseValue.getAngleC()));
            })
            .collect(Collectors.toList());

        double ipMax = Double.NaN;
        double ipMin = Double.NaN;
        double deltaCurrentIpMin = Double.NaN;
        double deltaCurrentIpMax = Double.NaN;
        FortescueValue current = ((FortescueFaultResult) faultResult).getCurrent();
        if (shortCircuitLimits != null) {
            ipMax = shortCircuitLimits.getIpMax();
            ipMin = shortCircuitLimits.getIpMin();
            deltaCurrentIpMin = current.getPositiveMagnitude() - ipMin / 1000;
            deltaCurrentIpMax = current.getPositiveMagnitude() - ipMax / 1000;
        }

        FortescueValue voltage = ((FortescueFaultResult) faultResult).getVoltage();
        //We here use the function toThreePhaseValue from the utils class instead of FortescueValue's one because it is curently privated by mistake, to be changed once Powsybl core 6.0.0 is out
        FortescueValue.ThreePhaseValue currentThreePhaseValue = ShortcircuitUtils.toThreePhaseValue(current);
        FortescueValue.ThreePhaseValue voltageThreePhaseValue = ShortcircuitUtils.toThreePhaseValue(voltage);
        FortescueResultEmbeddable fortescueCurrent = new FortescueResultEmbeddable(current.getPositiveMagnitude(), current.getZeroMagnitude(), current.getNegativeMagnitude(), current.getPositiveAngle(), current.getZeroAngle(), current.getNegativeAngle(), currentThreePhaseValue.getMagnitudeA(), currentThreePhaseValue.getMagnitudeB(), currentThreePhaseValue.getMagnitudeC(), currentThreePhaseValue.getAngleA(), currentThreePhaseValue.getAngleB(), currentThreePhaseValue.getAngleC());
        FortescueResultEmbeddable fortescueVoltage = new FortescueResultEmbeddable(voltage.getPositiveMagnitude(), voltage.getZeroMagnitude(), voltage.getNegativeMagnitude(), voltage.getPositiveAngle(), voltage.getZeroAngle(), voltage.getNegativeAngle(), voltageThreePhaseValue.getMagnitudeA(), voltageThreePhaseValue.getMagnitudeB(), voltageThreePhaseValue.getMagnitudeC(), voltageThreePhaseValue.getAngleA(), voltageThreePhaseValue.getAngleB(), voltageThreePhaseValue.getAngleC());
        return new FaultResultEntity(faultEmbedded, Double.NaN, shortCircuitPower, limitViolations, feederResults, ipMin, ipMax, fortescueCurrent, fortescueVoltage, deltaCurrentIpMin, deltaCurrentIpMax);
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
    public void insert(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitLimits> allCurrentLimits, boolean isWithFortescueResult, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result, allCurrentLimits, isWithFortescueResult));
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
    public Optional<ShortCircuitAnalysisResultEntity> findWithFaultResults(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findWithFaultResultsByResultUuid(resultUuid);
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitAnalysisResultEntity> findFullResults(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.findWithFaultResultsAndLimitViolationsByResultUuid(resultUuid);
        if (!result.isPresent()) {
            return result;
        }
        // using the the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!result.get().getFaultResults().isEmpty()) {
            resultRepository.findWithFaultResultsAndFeederResultsByResultUuid(resultUuid);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitAnalysisResultEntity> findResultsWithLimitViolations(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.findWithFaultResultsAndLimitViolationsByResultUuid(resultUuid);
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
    public Page<FaultResultEntity> findFaultResultsPage(ShortCircuitAnalysisResultEntity result, List<ResourceFilter> resourceFilters, Pageable pageable, FaultResultsMode mode) {
        Objects.requireNonNull(result);
        Specification<FaultResultEntity> specification = FaultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Page<FaultResultEntity> faultResultsPage = faultResultRepository.findAll(specification, pageable);
        if (faultResultsPage.hasContent() && mode != FaultResultsMode.BASIC) {
            appendLimitViolationsAndFeederResults(faultResultsPage);
        }
        return faultResultsPage;
    }

    @Transactional(readOnly = true)
    public Page<FeederResultEntity> findFeederResultsPage(ShortCircuitAnalysisResultEntity result, List<ResourceFilter> resourceFilters, Pageable pageable) {
        Objects.requireNonNull(result);
        Specification<FeederResultEntity> specification = FeederResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        return feederResultRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FaultResultEntity> findFaultResultsWithLimitViolationsPage(ShortCircuitAnalysisResultEntity result, List<ResourceFilter> resourceFilters, Pageable pageable) {
        Objects.requireNonNull(result);
        Specification<FaultResultEntity> specification = FaultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        specification = FaultResultSpecificationBuilder.appendWithLimitViolationsToSpecification(specification);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Page<FaultResultEntity> faultResultsPage = faultResultRepository.findAll(specification, pageable);
        if (faultResultsPage.hasContent()) {
            appendLimitViolationsAndFeederResults(faultResultsPage);
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
