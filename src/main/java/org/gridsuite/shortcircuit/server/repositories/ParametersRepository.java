/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import lombok.NonNull;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ParametersRepository extends JpaRepository<ShortCircuitParametersEntity, UUID> {
    default ShortCircuitParametersEntity getByIdOrDefault(@NonNull final UUID id) {
        return findById(id).orElseGet(() -> this.save(new ShortCircuitParametersEntity()));
    }
}
