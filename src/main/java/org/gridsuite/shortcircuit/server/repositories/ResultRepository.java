/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public interface ResultRepository extends JpaRepository<ShortCircuitAnalysisResultEntity, UUID> {
    Optional<ShortCircuitAnalysisResultEntity> findByResultUuid(UUID resultUuid);

    @EntityGraph(attributePaths = {"faultResults"}, type = EntityGraphType.LOAD)
    Optional<ShortCircuitAnalysisResultEntity> findWithFaultResultsByResultUuid(UUID resultUuid);

    @EntityGraph(attributePaths = {"faultResults", "faultResults.limitViolations"}, type = EntityGraphType.LOAD)
    Optional<ShortCircuitAnalysisResultEntity> findWithFaultResultsAndLimitViolationsByResultUuid(UUID resultUuid);

    @EntityGraph(attributePaths = {"faultResults", "faultResults.feederResults"}, type = EntityGraphType.LOAD)
    Optional<ShortCircuitAnalysisResultEntity> findWithFaultResultsAndFeederResultsByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);
}
