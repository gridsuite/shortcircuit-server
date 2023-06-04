/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;

import java.util.UUID;
import java.util.List;
import java.util.Set;


/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com
 */
@Repository
public interface FaultResultRepository extends JpaRepository<FaultResultEntity, UUID> {
    @EntityGraph(attributePaths = {"feederResults"}, type = EntityGraphType.LOAD)
    Set<FaultResultEntity> findAllWithFeederResultsByFaultResultUuidIn(List<UUID> faultResultsUUID);

}