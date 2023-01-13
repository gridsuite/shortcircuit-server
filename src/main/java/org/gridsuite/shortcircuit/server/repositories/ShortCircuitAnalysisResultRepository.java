/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.Fault;
import com.powsybl.shortcircuit.FaultResult;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import org.gridsuite.shortcircuit.server.entities.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public class ShortCircuitAnalysisResultRepository {

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;

    public ShortCircuitAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                                ResultRepository resultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
    }

    private static ShortCircuitAnalysisResultEntity toResultEntity(UUID resultUuid, ShortCircuitAnalysisResult result) {
        List<FaultResultEntity> faultResults = result.getFaultResults().stream().map(ShortCircuitAnalysisResultRepository::toFaultResultEntity).collect(Collectors.toList());
        return new ShortCircuitAnalysisResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC), faultResults);
    }

    private static FaultResultEntity toFaultResultEntity(FaultResult faultResult) {
        Fault fault = faultResult.getFault();
        double current = faultResult.getCurrent().getDirectMagnitude();
        double shortCircuitPower = faultResult.getShortCircuitPower();
        FaultEmbeddable faultEmbedded = new FaultEmbeddable(fault.getId(), fault.getElementId(), fault.getFaultType());

        List<LimitViolationEmbeddable> limitViolations = faultResult.getLimitViolations().stream().map(limitViolation ->
                new LimitViolationEmbeddable(limitViolation.getSubjectId(), limitViolation.getLimitType(), limitViolation.getLimit(),
                        limitViolation.getLimitName(), limitViolation.getValue()))
                .collect(Collectors.toList());

        List<FeederResultEmbeddable> feederResults = faultResult.getFeederResults().stream().map(feederResult ->
                new FeederResultEmbeddable(feederResult.getConnectableId(), feederResult.getCurrent().getDirectMagnitude()))
                .collect(Collectors.toList());

        return new FaultResultEntity(null, faultEmbedded, current, shortCircuitPower, limitViolations, feederResults);
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
    public void insert(UUID resultUuid, ShortCircuitAnalysisResult result) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result));
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
        return Optional.ofNullable(resultRepository.findByResultUuid(resultUuid));
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
