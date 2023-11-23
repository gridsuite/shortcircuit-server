/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;


/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com
 */
@Repository
public interface FaultResultRepository extends JpaRepository<FaultResultEntity, UUID>, JpaSpecificationExecutor<FaultResultEntity> {
    @EntityGraph(attributePaths = {"limitViolations"}, type = EntityGraphType.LOAD)
    Set<FaultResultEntity> findAllWithLimitViolationsByFaultResultUuidIn(List<UUID> faultResultsUUID);

    @Modifying
    @EntityGraph(attributePaths = {"feederResults"}, type = EntityGraphType.LOAD)
    Set<FaultResultEntity> findAllWithFeederResultsByFaultResultUuidIn(List<UUID> faultResultsUUID);

    @Modifying
    @Query(value = "DELETE FROM fault_result_entity WHERE result_result_uuid = ?1", nativeQuery = true)
    void deleteFaultResultsByShortCircuitResultUUid(UUID resultUuid);

    @Modifying
    @Query(value = "DELETE FROM limit_violations WHERE fault_result_entity_fault_result_uuid IN (SELECT fault_result_uuid FROM fault_result_entity where result_result_uuid = ?1)", nativeQuery = true)
    void deleteLimitViolationsByShortCircuitResultUUid(UUID resultUuid);

    @Modifying
    @Query(value = "DELETE FROM feeder_results WHERE fault_result_entity_fault_result_uuid IN (SELECT fault_result_uuid FROM fault_result_entity where result_result_uuid = ?1)", nativeQuery = true)
    void deleteFeederResultsByShortCircuitResultUUid(UUID resultUuid);

}
