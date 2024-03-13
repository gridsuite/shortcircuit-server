/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories.specifications;

import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@Service
public final class FeederResultSpecificationBuilder {

    public Specification<FeederResultEntity> resultUuidEquals(UUID value) {
        return (feederResult, cq, cb) -> cb.equal(feederResult.get("faultResult").get("result").get("resultUuid"), value);
    }

    public Specification<FeederResultEntity> buildSpecification(UUID resultUuid, List<ResourceFilter> resourceFilters) {
        Specification<FeederResultEntity> specification = Specification.where(resultUuidEquals(resultUuid));
        return SpecificationUtils.appendFiltersToSpecification(specification, resourceFilters);
    }
}
