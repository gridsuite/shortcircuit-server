/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.iidm.network.ThreeSides;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@Repository
public interface FeederResultRepository extends JpaRepository<FeederResultEntity, UUID>, JpaSpecificationExecutor<FeederResultEntity> {
    @Query(value = "SELECT DISTINCT fr.side FROM feeder_results fr " +
            "JOIN fault_result_entity fre ON fr.fault_result_entity_fault_result_uuid = fre.fault_result_uuid " +
            "JOIN shortcircuit_result scr ON fre.result_result_uuid = scr.result_uuid " +
            "WHERE scr.result_uuid = :resultUuid AND fr.side != ''", nativeQuery = true)
    List<ThreeSides> findBranchSides(UUID resultUuid);
}
