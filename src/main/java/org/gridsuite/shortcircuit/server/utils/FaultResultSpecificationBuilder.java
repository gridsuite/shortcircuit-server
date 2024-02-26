/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public final class FaultResultSpecificationBuilder {

    // Utility class, so no constructor
    private FaultResultSpecificationBuilder() {
    }

    public static Specification<FaultResultEntity> resultUuidEquals(UUID value) {
        return (faultResult, cq, cb) -> cb.equal(faultResult.get("result").get("resultUuid"), value);
    }

    public static Specification<FaultResultEntity> buildSpecification(UUID resultUuid, List<ResourceFilter> resourceFilters) {
        return (final Root<FaultResultEntity> root, final CriteriaQuery<?> query, final CriteriaBuilder builder) -> {
            query.distinct(true);

            Specification<FaultResultEntity> specification = Specification.where(resultUuidEquals(resultUuid));
            List<ResourceFilter> parentsFilters = resourceFilters.stream()
                    .map(filter -> {
                        if (FeederResultEntity.Fields.connectableId.equals(filter.column())) {
                            // this column belong to the feederResults "sub-columns"
                            // as a resource filter they will filter out when a FaultResult has no feeder result matching the filter
                            // BUT individual feeders are not filtered within each Fault Result
                            return new ResourceFilter(filter.dataType(),
                                    filter.type(),
                                    filter.value(),
                                    "feederResults." + filter.column());
                        } else {
                            // regular filters
                            return filter;
                        }
                    }).toList();
            return SpecificationUtils.appendFiltersToSpecification(specification, parentsFilters).toPredicate(root, query, builder);
        };
    }

    public static Specification<FaultResultEntity> appendWithLimitViolationsToSpecification(Specification<FaultResultEntity> specification) {
        return specification.and(SpecificationUtils.isNotEmpty("limitViolations"));
    }
}
