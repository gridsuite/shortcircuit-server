/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.Fault;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;


/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Repository
public interface FaultResultRepository extends JpaRepository<FaultResultEntity, UUID>, JpaSpecificationExecutor<FaultResultEntity> {
    @EntityGraph(attributePaths = {"limitViolations"}, type = EntityGraphType.LOAD)
    Set<FaultResultEntity> findAllWithLimitViolationsByFaultResultUuidIn(List<UUID> faultResultsUUID);

    @EntityGraph(attributePaths = {"feederResults"}, type = EntityGraphType.LOAD)
    List<FaultResultEntity> findAll(Specification<FaultResultEntity> specification);

    @EntityGraph(attributePaths = {"feederResults"}, type = EntityGraphType.LOAD)
    Set<FaultResultEntity> findAllWithFeederResultsByFaultResultUuidIn(List<UUID> faultResultsUUID);

    @Query(value = "SELECT faultResultUuid FROM FaultResultEntity WHERE result.resultUuid = ?1")
    Set<UUID> findAllFaultResultUuidsByShortCircuitResultUuid(UUID resultUuid);

    List<FaultResultEntity> findAllByResultResultUuidAndFaultVoltageLevelId(UUID resultUuid, String voltageLevelId);

    // From: https://www.baeldung.com/spring-data-jpa-deleteby
    // "The @Query method creates a single SQL query against the database. By comparison, the deleteBy methods execute a read query, then delete each of the items one by one."
    // As we need here to delete thousands of fault results, using native SQL query was required for having decent performance.
    @Modifying
    @Query(value = "DELETE FROM fault_result_entity WHERE result_result_uuid = ?1", nativeQuery = true)
    void deleteFaultResultsByShortCircuitResultUUid(UUID resultUuid);

    @Modifying
    @Query(value = "DELETE FROM limit_violations WHERE fault_result_entity_fault_result_uuid IN ?1", nativeQuery = true)
    void deleteLimitViolationsByFaultResultUuids(Set<UUID> ids);

    // We keep this method in this repository instead of FeederResultRepository to help readability as it is executed with the two queries above.
    @Modifying
    @Query(value = "DELETE FROM feeder_results WHERE fault_result_entity_fault_result_uuid IN ?1", nativeQuery = true)
    void deleteFeederResultsByFaultResultUuids(Set<UUID> ids);

    List<FaultResultEntity> findAllByFaultResultUuidIn(List<UUID> uuids);

    interface EntityId {
        UUID getFaultResultUuid();
    }

    @Query(value = " SELECT DISTINCT limit_Type FROM fault_result_entity " +
            " where result_result_uuid = :resultUuid AND limit_Type not like ''" +
            "order by limit_Type", nativeQuery = true)
    List<LimitViolationType> findLimitTypes(UUID resultUuid);

    @Query(value = " SELECT DISTINCT fault_Type FROM fault_result_entity " +
            " where result_result_uuid = :resultUuid AND fault_Type not like ''" +
            "order by fault_Type", nativeQuery = true)
    List<Fault.FaultType> findFaultTypes(UUID resultUuid);
}
