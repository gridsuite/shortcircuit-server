/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public final class FaultResultSpecificationBuilder {

    // the following column names that belongs to the feeder results ie children of each of the main Fault results
    private static final ArrayList<String> FEEDERS_COLS = new ArrayList<>(List.of(
            "connectableId"));

    // Utility class, so no constructor
    private FaultResultSpecificationBuilder() {
    }

    public static Specification<FaultResultEntity> resultUuidEquals(UUID value) {
        return (faultResult, cq, cb) -> cb.equal(faultResult.get("result").get("resultUuid"), value);
    }

    public static Specification<FaultResultEntity> buildSpecification(UUID resultUuid, List<ResourceFilter> resourceFilters) {
        Specification<FaultResultEntity> specification = Specification.where(resultUuidEquals(resultUuid));

        List<ResourceFilter> parentsFilters = resourceFilters.stream()
                .map(filter -> {
                    if (FEEDERS_COLS.contains(filter.column())) {
                        // those column belong to the feederResults "sub-columns" => a conversion has to be done
                        // ===> on the front side those column are handled in 'const flattenResult = useCallback' in shortcircuit-analysis-result-table.tsx
                        return new ResourceFilter(filter.dataType(),
                                filter.type(),
                                filter.value(),
                                "feederResults." + filter.column());
                    } else {
                        // regular filters
                        return filter;
                    }
                }).toList();
        return SpecificationUtils.appendFiltersToSpecification(specification, parentsFilters);
    }

    public static Specification<FaultResultEntity> appendWithLimitViolationsToSpecification(Specification<FaultResultEntity> specification) {
        return specification.and(SpecificationUtils.isNotEmpty("limitViolations"));
    }
}
