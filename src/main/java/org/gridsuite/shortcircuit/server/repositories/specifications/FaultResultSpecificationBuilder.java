/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.utils.specification.SpecificationUtils;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public final class FaultResultSpecificationBuilder {

    // Utility class, so no constructor
    private FaultResultSpecificationBuilder() { }

    public static boolean isNotParentFilter(ResourceFilterDTO filter) {
        return filter.column().contains(FeederResultEntity.Fields.connectableId);
    }

    public static Specification<FaultResultEntity> uuidIn(List<UUID> uuids) {
        return (root, cq, cb) -> root.get(getIdFieldName()).in(uuids);
    }

    public static Specification<FaultResultEntity> resultUuidEquals(UUID value) {
        return (root, cq, cb) -> cb.equal(getResultIdPath(root), value);
    }

    public static String getIdFieldName() {
        return FaultResultEntity.Fields.faultResultUuid;
    }

    public static Path<UUID> getResultIdPath(Root<FaultResultEntity> root) {
        return root.get(FaultResultEntity.Fields.result).get(ShortCircuitAnalysisResultEntity.Fields.resultUuid);
    }

    public static Specification<FaultResultEntity> appendWithLimitViolationsToSpecification(Specification<FaultResultEntity> specification) {
        return specification.and(SpecificationUtils.isNotEmpty(FaultResultEntity.Fields.limitViolations));
    }

    public static Specification<FaultResultEntity> buildSpecification(UUID resultUuid, List<ResourceFilterDTO> resourceFilters) {
        // since sql joins generates duplicate results, we need to use distinct here
        Specification<FaultResultEntity> specification = SpecificationUtils.distinct();
        // filter by resultUuid
        specification = specification.and(Specification.where(resultUuidEquals(resultUuid)));

        return SpecificationUtils.appendFiltersToSpecification(specification, resourceFilters);
    }

    public static Specification<FaultResultEntity> buildFeedersSpecification(List<UUID> uuids, List<ResourceFilterDTO> resourceFilters) {
        List<ResourceFilterDTO> childrenResourceFilter = resourceFilters.stream().filter(FaultResultSpecificationBuilder::isNotParentFilter)
                .toList();
        Specification<FaultResultEntity> specification = Specification.where(uuidIn(uuids));

        return SpecificationUtils.appendFiltersToSpecification(specification, childrenResourceFilter);
    }
}
