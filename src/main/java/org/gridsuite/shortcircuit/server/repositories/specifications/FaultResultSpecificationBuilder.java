/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories.specifications;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.LimitViolationEmbeddable;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@Service
public class FaultResultSpecificationBuilder extends AbstractCommonSpecificationBuilder<FaultResultEntity> {
    @Override
    public boolean isNotParentFilter(ResourceFilter filter) {
        return filter.column().contains(FeederResultEntity.Fields.connectableId) ||
                filter.column().contains(LimitViolationEmbeddable.Fields.limitType);
    }

    @Override
    public String getIdFieldName() {
        return FaultResultEntity.Fields.faultResultUuid;
    }

    @Override
    public Path<UUID> getResultIdPath(Root<FaultResultEntity> root) {
        return root.get(FaultResultEntity.Fields.result).get(ShortCircuitAnalysisResultEntity.Fields.resultUuid);
    }

    public Specification<FaultResultEntity> childrenNotEmpty() {
        return SpecificationUtils.isNotEmpty(FaultResultEntity.Fields.feederResults);
    }

    public Specification<FaultResultEntity> appendWithLimitViolationsToSpecification(Specification<FaultResultEntity> specification) {
        return specification.and(SpecificationUtils.isNotEmpty("limitViolations"));
    }

    public Specification<FaultResultEntity> buildFeedersSpecification(List<UUID> uuids, List<ResourceFilter> resourceFilters) {
        List<ResourceFilter> childrenResourceFilter = resourceFilters.stream().filter(this::isNotParentFilter).toList();
        Specification<FaultResultEntity> specification = Specification.where(uuidIn(uuids));

        return SpecificationUtils.appendFiltersToSpecification(specification, childrenResourceFilter);
    }
}
