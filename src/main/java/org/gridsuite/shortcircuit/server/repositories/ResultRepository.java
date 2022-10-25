/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.ResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@Repository
public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
    ResultEntity findByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);
}
