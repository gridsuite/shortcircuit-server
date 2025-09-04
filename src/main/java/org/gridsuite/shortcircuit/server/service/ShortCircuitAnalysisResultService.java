/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractComputationResultService;
import org.gridsuite.shortcircuit.server.dto.FaultResultsMode;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.repositories.FaultResultRepository;
import org.gridsuite.shortcircuit.server.repositories.FeederResultRepository;
import org.gridsuite.shortcircuit.server.repositories.GlobalStatusRepository;
import org.gridsuite.shortcircuit.server.repositories.ResultRepository;
import org.gridsuite.shortcircuit.server.repositories.specifications.FaultResultSpecificationBuilder;
import org.gridsuite.shortcircuit.server.repositories.specifications.FeederResultSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Slf4j
@AllArgsConstructor
@Service
public class ShortCircuitAnalysisResultService extends AbstractComputationResultService<ShortCircuitAnalysisStatus> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitAnalysisResultService.class);
    private final GlobalStatusRepository globalStatusRepository;
    private final ResultRepository resultRepository;
    private final FaultResultRepository faultResultRepository;
    private final FeederResultRepository feederResultRepository;

    private static final String DEFAULT_FAULT_RESULT_SORT_COLUMN = "faultResultUuid";

    private static final String DEFAULT_FEEDER_RESULT_SORT_COLUMN = "feederResultUuid";

    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;

    private final FaultResultSpecificationBuilder faultResultSpecificationBuilder;

    private static List<LimitViolationEmbeddable> extractLimitViolations(FaultResult faultResult) {
        return faultResult.getLimitViolations().stream()
            .map(limitViolation -> new LimitViolationEmbeddable(limitViolation.getSubjectId(),
                limitViolation.getLimitType(), limitViolation.getLimit(),
                limitViolation.getLimitName(), limitViolation.getValue()))
            .toList();
    }

    public static ShortCircuitAnalysisResultEntity toResultEntity(UUID resultUuid, ShortCircuitAnalysisResult result, Map<String, ShortCircuitLimits> allShortCircuitLimits) {
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
        return new ShortCircuitAnalysisResultEntity(resultUuid, Instant.now().truncatedTo(ChronoUnit.MICROS), faultResults, null);
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
                .map(feederResult -> new FeederResultEntity(feederResult.getConnectableId(),
                        ((MagnitudeFeederResult) feederResult).getCurrent(), null, feederResult.getSide()))
                .toList());
        if (shortCircuitLimits != null) {
            entity.setDeltaCurrentIpMin(current - entity.getIpMin());
            entity.setDeltaCurrentIpMax(current - entity.getIpMax());
        }
        return entity;
    }

    private static FaultResultEntity toFortescueFaultResultEntity(FortescueFaultResult faultResult, ShortCircuitLimits shortCircuitLimits) {
        FaultResultEntity entity = toGenericFaultResultEntity(faultResult, shortCircuitLimits);
        entity.setFeederResults(faultResult.getFeederResults().stream()
                .map(feederResult -> {
                    final FortescueValue feederFortescueCurrent = ((FortescueFeederResult) feederResult).getCurrent();
                    final FortescueValue.ThreePhaseValue feederFortescueThreePhaseValue = feederFortescueCurrent.toThreePhaseValue();
                    return new FeederResultEntity(feederResult.getConnectableId(), Double.NaN, new FortescueResultEmbeddable(
                            feederFortescueCurrent.getPositiveMagnitude(), feederFortescueCurrent.getZeroMagnitude(),
                            feederFortescueCurrent.getNegativeMagnitude(), feederFortescueCurrent.getPositiveAngle(),
                            feederFortescueCurrent.getZeroAngle(), feederFortescueCurrent.getNegativeAngle(),
                            feederFortescueThreePhaseValue.getMagnitudeA(), feederFortescueThreePhaseValue.getMagnitudeB(),
                            feederFortescueThreePhaseValue.getMagnitudeC(), feederFortescueThreePhaseValue.getAngleA(),
                            feederFortescueThreePhaseValue.getAngleB(), feederFortescueThreePhaseValue.getAngleC()), feederResult.getSide());
                })
                .toList());

        final FortescueValue current = faultResult.getCurrent();
        if (shortCircuitLimits != null) {
            entity.setDeltaCurrentIpMin(current.getPositiveMagnitude() - entity.getIpMin());
            entity.setDeltaCurrentIpMax(current.getPositiveMagnitude() - entity.getIpMax());
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
            .map(uuid -> toStatusEntity(uuid, status)).toList());
    }

    @Transactional(readOnly = true)
    public List<LimitViolationType> findLimitTypes(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return faultResultRepository.findLimitTypes(resultUuid);
    }

    @Transactional(readOnly = true)
    public List<Fault.FaultType> findFaultTypes(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return faultResultRepository.findFaultTypes(resultUuid);
    }

    @Transactional
    public void insert(UUID resultUuid, ShortCircuitAnalysisResult result, ShortCircuitRunContext runContext, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null && (runContext.getBusId() != null ||
                        !result.getFaultResults().stream().map(FaultResult::getStatus).allMatch(FaultResult.Status.NO_SHORT_CIRCUIT_DATA::equals))
        ) {
            resultRepository.save(toResultEntity(resultUuid, result, runContext.getShortCircuitLimits()));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Override
    public void insertStatus(List<UUID> resultUuids, ShortCircuitAnalysisStatus status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status.name())).toList());
    }

    @Override
    @Transactional
    public void delete(UUID resultUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        deleteShortCircuitResult(resultUuid);
        LOGGER.info("Shortcircuit result '{}' has been deleted in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    private void deleteShortCircuitResult(UUID resultUuid) {
        Set<UUID> faultResultUuids = faultResultRepository.findAllFaultResultUuidsByShortCircuitResultUuid(resultUuid);
        faultResultRepository.deleteFeederResultsByFaultResultUuids(faultResultUuids);
        faultResultRepository.deleteLimitViolationsByFaultResultUuids(faultResultUuids);
        faultResultRepository.deleteFaultResultsByShortCircuitResultUUid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Override
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
        if (result.isEmpty()) {
            return result;
        }
        // using the Hibernate First-Level Cache or Persistence Context
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
        if (result.isEmpty()) {
            return result;
        }
        List<UUID> faultResultsUuidWithLimitViolations = result.get().getFaultResults().stream()
                                                            .filter(fr -> !fr.getLimitViolations().isEmpty())
                                                            .map(FaultResultEntity::getFaultResultUuid)
                                                            .toList();
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!result.get().getFaultResults().isEmpty()) {
            faultResultRepository.findAllWithFeederResultsByFaultResultUuidIn(faultResultsUuidWithLimitViolations);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<FaultResultEntity> findFaultResultsPage(ShortCircuitAnalysisResultEntity result,
                                                        List<ResourceFilterDTO> resourceFilters,
                                                        Pageable pageable,
                                                        FaultResultsMode mode) {
        Objects.requireNonNull(result);

        Optional<Sort.Order> childrenSort = extractChildrenSort(pageable);

        Pageable modifiedPageable = addDefaultSort(filterOutChildrenSort(pageable, childrenSort),
                DEFAULT_FAULT_RESULT_SORT_COLUMN);
        Specification<FaultResultEntity> specification = faultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch

        Page<FaultResultRepository.EntityId> uuidPage = faultResultRepository.findBy(specification, q ->
                q.project(FaultResultEntity.Fields.faultResultUuid)
                        .as(FaultResultRepository.EntityId.class)
                        .sortBy(modifiedPageable.getSort())
                        .page(modifiedPageable)
        );

        if (!uuidPage.hasContent()) {
            return Page.empty();
        }

        List<UUID> faultResultsUuids = uuidPage
                .map(FaultResultRepository.EntityId::getFaultResultUuid)
                .toList();
        // Then we fetch the main entities data for each UUID
        List<FaultResultEntity> faultResults = faultResultRepository.findAllByFaultResultUuidIn(faultResultsUuids);
        faultResults.sort(Comparator.comparing(fault -> faultResultsUuids.indexOf(fault.getFaultResultUuid())));
        Page<FaultResultEntity> faultResultsPage = new PageImpl<>(faultResults, modifiedPageable, uuidPage.getTotalElements());

        if (mode != FaultResultsMode.BASIC) {
            // then we append the missing data, and filter some of the Lazy Loaded collections
            appendLimitViolationsAndFeederResults(faultResultsPage, childrenSort, resourceFilters);
        }

        return faultResultsPage;
    }

    private Pageable filterOutChildrenSort(Pageable pageable, Optional<Sort.Order> childrenSort) {
        if (childrenSort.isEmpty()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(pageable.getSort().stream().filter(sortOrder ->
                                !sortOrder.getProperty().equals(childrenSort.get().getProperty())
                        ).toList()
                )
        );
    }

    private Optional<Sort.Order> extractChildrenSort(Pageable pageable) {
        return pageable.getSort().stream()
                .filter(sortOrder ->
                        sortOrder.getProperty().contains(FeederResultEntity.Fields.connectableId))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Page<FeederResultEntity> findFeederResultsPage(ShortCircuitAnalysisResultEntity result, List<ResourceFilterDTO> resourceFilters, Pageable pageable) {
        Objects.requireNonNull(result);
        Specification<FeederResultEntity> specification = FeederResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        return feederResultRepository.findAll(specification, addDefaultSort(pageable, DEFAULT_FEEDER_RESULT_SORT_COLUMN));
    }

    @Transactional(readOnly = true)
    public List<ThreeSides> findBranchSides(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return feederResultRepository.findBranchSides(resultUuid);
    }

    @Transactional(readOnly = true)
    public Page<FaultResultEntity> findFaultResultsWithLimitViolationsPage(ShortCircuitAnalysisResultEntity result,
                                                                           List<ResourceFilterDTO> resourceFilters,
                                                                           Pageable pageable) {
        Objects.requireNonNull(result);

        Optional<Sort.Order> childrenSort = extractChildrenSort(pageable);

        Pageable modifiedPageable = addDefaultSort(filterOutChildrenSort(pageable, childrenSort),
                DEFAULT_FAULT_RESULT_SORT_COLUMN);
        Specification<FaultResultEntity> specification = faultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        specification = faultResultSpecificationBuilder.appendWithLimitViolationsToSpecification(specification);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch

        Page<FaultResultRepository.EntityId> uuidPage = faultResultRepository.findBy(specification, q ->
                q.project(FaultResultEntity.Fields.faultResultUuid)
                        .as(FaultResultRepository.EntityId.class)
                        .sortBy(modifiedPageable.getSort())
                        .page(modifiedPageable)
        );

        if (!uuidPage.hasContent()) {
            return Page.empty();
        }

        List<UUID> faultResultsUuids = uuidPage
                .map(FaultResultRepository.EntityId::getFaultResultUuid)
                .toList();
        // Then we fetch the main entities data for each UUID
        List<FaultResultEntity> faultResults = faultResultRepository.findAllByFaultResultUuidIn(faultResultsUuids);
        faultResults.sort(Comparator.comparing(fault -> faultResultsUuids.indexOf(fault.getFaultResultUuid())));
        Page<FaultResultEntity> faultResultsPage = new PageImpl<>(faultResults, modifiedPageable, uuidPage.getTotalElements());

        // then we append the missing data, and filter some of the Lazy Loaded collections
        appendLimitViolationsAndFeederResults(faultResultsPage, childrenSort, resourceFilters);

        return faultResultsPage;
    }

    private void appendLimitViolationsAndFeederResults(Page<FaultResultEntity> pagedFaultResults,
                                                       Optional<Sort.Order> childrenSort,
                                                       List<ResourceFilterDTO> resourceFilters) {
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!pagedFaultResults.isEmpty()) {
            List<UUID> faultResultsUuids = pagedFaultResults.stream()
                    .map(FaultResultEntity::getFaultResultUuid)
                    .toList();

            Specification<FaultResultEntity> specification = faultResultSpecificationBuilder.buildFeedersSpecification(faultResultsUuids, resourceFilters);
            faultResultRepository.findAll(specification);

            faultResultRepository.findAllWithLimitViolationsByFaultResultUuidIn(faultResultsUuids);

            sortFeeders(pagedFaultResults, childrenSort);
        }
    }

    private void sortFeeders(Page<FaultResultEntity> pagedFaultResults, Optional<Sort.Order> childrenSort) {
        // feeders may only be sorted by connectableId
        if (childrenSort.isPresent()) {
            pagedFaultResults.forEach(res -> res.getFeederResults().sort(
                childrenSort.get().isAscending() ?
                    Comparator.comparing(FeederResultEntity::getConnectableId) :
                    Comparator.comparing(FeederResultEntity::getConnectableId).reversed()
            ));
        } else {
            // otherwise by default feederResults (within each individual faultResult) are sorted by 'current' in descending order :
            pagedFaultResults.forEach(res -> res.getFeederResults().sort(
                    Comparator.comparingDouble(FeederResultEntity::getCurrent).reversed()));
        }
    }

    @Transactional(readOnly = true)
    @Override
    public ShortCircuitAnalysisStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return ShortCircuitAnalysisStatus.valueOf(globalEntity.getStatus());
        } else {
            return null;
        }
    }

    private Pageable addDefaultSort(Pageable pageable, String defaultSortColumn) {
        if (pageable.isPaged() && pageable.getSort().getOrderFor(defaultSortColumn) == null) {
            //if it's already sorted by our defaultColumn we don't add another sort by the same column
            Sort finalSort = pageable.getSort().and(Sort.by(DEFAULT_SORT_DIRECTION, defaultSortColumn));
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
        }
        //nothing to do if the request is not paged
        return pageable;
    }

    @Override
    @Transactional
    public void saveDebugFileLocation(UUID resultUuid, String debugFilePath) {
        int updatedRows = resultRepository.updateDebugFileLocation(resultUuid, debugFilePath);
        if (updatedRows == 0) {
            resultRepository.save(new ShortCircuitAnalysisResultEntity(resultUuid, null, null, debugFilePath));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String findDebugFileLocation(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findById(resultUuid)
                .map(ShortCircuitAnalysisResultEntity::getDebugFileLocation)
                .orElse(null);
    }
}
