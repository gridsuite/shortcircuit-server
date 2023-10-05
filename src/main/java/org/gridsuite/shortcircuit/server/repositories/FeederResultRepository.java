/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@Repository
public interface FeederResultRepository extends JpaRepository<FeederResultEntity, UUID> {
    Optional<Page<FeederResultEntity>> findPagedByFaultResult(FaultResultEntity faultResult, Pageable pageable);
}
