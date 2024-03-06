/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.dto.FaultResultsMode;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.repositories.specifications.FaultResultSpecificationBuilder;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.gridsuite.shortcircuit.server.repositories.specifications.FeederResultSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Slf4j
@Repository
public class ShortCircuitAnalysisResultRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitAnalysisResultRepository.class);
    private final GlobalStatusRepository globalStatusRepository;
    private final ResultRepository resultRepository;
    private final FaultResultRepository faultResultRepository;
    private final FeederResultRepository feederResultRepository;
    private final FeederResultSpecificationBuilder feederResultSpecificationBuilder;
    private final FaultResultSpecificationBuilder faultResultSpecificationBuilder;

    private static final String DEFAULT_FAULT_RESULT_SORT_COLUMN = "faultResultUuid";

    private static final String DEFAULT_FEEDER_RESULT_SORT_COLUMN = "feederResultUuid";

    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;

    @Autowired
    public ShortCircuitAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                                ResultRepository resultRepository,
                                                FaultResultRepository faultResultRepository,
                                                FeederResultRepository feederResultRepository,
                                                FaultResultSpecificationBuilder faultResultSpecificationBuilder,
                                                FeederResultSpecificationBuilder feederResultSpecificationBuilder) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
        this.faultResultRepository = faultResultRepository;
        this.feederResultRepository = feederResultRepository;
        this.faultResultSpecificationBuilder = faultResultSpecificationBuilder;
        this.feederResultSpecificationBuilder = feederResultSpecificationBuilder;
    }

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
                .map(feederResult -> new FeederResultEntity(feederResult.getConnectableId(),
                        ((MagnitudeFeederResult) feederResult).getCurrent(), null))
                .toList());
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
                    return new FeederResultEntity(feederResult.getConnectableId(), Double.NaN, new FortescueResultEmbeddable(
                            feederFortescueCurrent.getPositiveMagnitude(), feederFortescueCurrent.getZeroMagnitude(),
                            feederFortescueCurrent.getNegativeMagnitude(), feederFortescueCurrent.getPositiveAngle(),
                            feederFortescueCurrent.getZeroAngle(), feederFortescueCurrent.getNegativeAngle(),
                            feederFortescueThreePhaseValue.getMagnitudeA(), feederFortescueThreePhaseValue.getMagnitudeB(),
                            feederFortescueThreePhaseValue.getMagnitudeC(), feederFortescueThreePhaseValue.getAngleA(),
                            feederFortescueThreePhaseValue.getAngleB(), feederFortescueThreePhaseValue.getAngleC()));
                })
                .toList());

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
            .map(uuid -> toStatusEntity(uuid, status)).toList());
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
                                                        List<ResourceFilter> resourceFilters,
                                                        Pageable pageable,
                                                        FaultResultsMode mode) {
        Objects.requireNonNull(result);

        Optional<Sort.Order> secondarySort = extractSecondarySort(pageable);

        Specification<FaultResultEntity> specification = faultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Page<FaultResultEntity> faultResultsPage = faultResultRepository.findAll(
                specification, addDefaultSort(
                        filterOutChildrenSort(pageable, secondarySort),
                        DEFAULT_FAULT_RESULT_SORT_COLUMN
                ));
        if (faultResultsPage.hasContent() && mode != FaultResultsMode.BASIC) {
            appendLimitViolationsAndFeederResults(faultResultsPage, secondarySort, resourceFilters);
        }
        return faultResultsPage;
    }

    private Pageable filterOutChildrenSort(Pageable pageable, Optional<Sort.Order> secondarySort) {
        if (secondarySort.isEmpty()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(pageable.getSort().stream().filter(sortOrder ->
                                !sortOrder.getProperty().equals(secondarySort.get().getProperty())
                        ).toList()
                )
        );
    }

    private Optional<Sort.Order> extractSecondarySort(Pageable pageable) {
        return pageable.getSort().stream()
                .filter(sortOrder ->
                        sortOrder.getProperty().contains(FeederResultEntity.Fields.connectableId))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Page<FeederResultEntity> findFeederResultsPage(ShortCircuitAnalysisResultEntity result, List<ResourceFilter> resourceFilters, Pageable pageable) {
        Objects.requireNonNull(result);
        Specification<FeederResultEntity> specification = feederResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        return feederResultRepository.findAll(specification, addDefaultSort(pageable, DEFAULT_FEEDER_RESULT_SORT_COLUMN));
    }

    @Transactional(readOnly = true)
    public Page<FaultResultEntity> findFaultResultsWithLimitViolationsPage(ShortCircuitAnalysisResultEntity result,
                                                                           List<ResourceFilter> resourceFilters,
                                                                           Pageable pageable) {
        Objects.requireNonNull(result);

        Optional<Sort.Order> secondarySort = extractSecondarySort(pageable);

        Specification<FaultResultEntity> specification = faultResultSpecificationBuilder.buildSpecification(result.getResultUuid(), resourceFilters);
        specification = faultResultSpecificationBuilder.appendWithLimitViolationsToSpecification(specification);
        // WARN org.hibernate.hql.internal.ast.QueryTranslatorImpl -
        // HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
        // cf. https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
        // We must separate in two requests, one with pagination the other one with Join Fetch
        Page<FaultResultEntity> faultResultsPage = faultResultRepository.findAll(
                specification,
                addDefaultSort(
                        filterOutChildrenSort(pageable, secondarySort),
                        DEFAULT_FAULT_RESULT_SORT_COLUMN
                ));
        if (faultResultsPage.hasContent()) {
            appendLimitViolationsAndFeederResults(faultResultsPage, secondarySort, resourceFilters);
        }
        return faultResultsPage;
    }

    private void appendLimitViolationsAndFeederResults(Page<FaultResultEntity> pagedFaultResults,
                                                       Optional<Sort.Order> secondarySort,
                                                       List<ResourceFilter> resourceFilters) {
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        if (!pagedFaultResults.isEmpty()) {
            List<UUID> faultResultsUuids = pagedFaultResults.stream()
                    .map(FaultResultEntity::getFaultResultUuid)
                    .toList();

            faultResultRepository.findAllWithLimitViolationsByFaultResultUuidIn(faultResultsUuids);

            faultResultRepository.findAllWithFeederResultsByFaultResultUuidIn(faultResultsUuids);
            sortFeeders(pagedFaultResults, secondarySort);
            filterFeeders(pagedFaultResults, resourceFilters);
        }
    }

    private void filterFeeders(Page<FaultResultEntity> pagedFaultResults, List<ResourceFilter> resourceFilters) {
        // feeders may only be filtered through connectableId
        Optional<ResourceFilter> connectableIdFilter = resourceFilters.stream()
                .filter(filter -> filter.column().contains(FeederResultEntity.Fields.connectableId))
                .findFirst();
        connectableIdFilter.ifPresent(resourceFilter -> pagedFaultResults
                .map(faultRes -> {
                    List<FeederResultEntity> feeders = faultRes.getFeederResults()
                            .stream()
                            .filter(feeder -> feeder.match(resourceFilter)
                            ).toList();
                    faultRes.setFeederResults(feeders);
                    return faultRes;
                }));
    }

    private void sortFeeders(Page<FaultResultEntity> pagedFaultResults, Optional<Sort.Order> secondarySort) {
        // feeders may only be sorted by connectableId
        if (secondarySort.isPresent()) {
            pagedFaultResults.map(res -> {
                res.getFeederResults().sort(
                    secondarySort.get().isAscending() ?
                        Comparator.comparing(FeederResultEntity::getConnectableId) :
                        Comparator.comparing(FeederResultEntity::getConnectableId).reversed()
                );
                return res;
            });
        } else {
            // otherwise by default feederResults (within each individual faultResult) are sorted by 'current' in descending order :
            pagedFaultResults.map(res -> {
                res.getFeederResults().sort(
                        Comparator.comparingDouble(FeederResultEntity::getCurrent).reversed());
                return res;
            });
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

    private Pageable addDefaultSort(Pageable pageable, String defaultSortColumn) {
        if (pageable.isPaged() && pageable.getSort().getOrderFor(defaultSortColumn) == null) {
            //if it's already sorted by our defaultColumn we don't add another sort by the same column
            Sort finalSort = pageable.getSort().and(Sort.by(DEFAULT_SORT_DIRECTION, defaultSortColumn));
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
        }
        //nothing to do if the request is not paged
        return pageable;
    }
}
