/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.google.common.collect.Lists;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
@Slf4j
public class FilterService extends AbstractFilterService {

    private static final String WARN_UNEXPECTED_TYPE_ENCOUNTERED_FOR = "Unexpected type encountered for {} : {} - {}";
    private static final int CHUNK_SIZE = 10000;

    public FilterService(
            NetworkStoreService networkStoreService,
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(networkStoreService, filterServerBaseUri);
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        // Get equipment types from violation types
        List<EquipmentType> equipmentTypes = List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);

        // Call the common implementation with specific parameters
        return super.getResourceFilter(networkUuid, variantId, globalFilter, equipmentTypes, FaultResultEntity.Fields.feederResults + "." + FeederResultEntity.Fields.connectableId);
    }

    public <X> Specification<X> appendInToSpecification(Specification<X> specification, List<ResourceFilterDTO> resourceFilters) {
        Objects.requireNonNull(specification);
        if (resourceFilters != null && !resourceFilters.isEmpty()) {
            Specification<X> completedSpecification = specification;

            for (ResourceFilterDTO resourceFilter : resourceFilters) {
                if (resourceFilter.dataType() == ResourceFilterDTO.DataType.TEXT) {
                    completedSpecification = appendInTextFilterToSpecification(completedSpecification, resourceFilter);
                } else {
                    doLogWarn(resourceFilter);
                }
            }

            return completedSpecification;
        } else {
            return specification;
        }
    }

    private <X> Specification<X> appendInTextFilterToSpecification(Specification<X> specification, ResourceFilterDTO resourceFilter) {
        Specification<X> completedSpecification = specification;
        if (resourceFilter.type() == ResourceFilterDTO.Type.IN) {
            if (resourceFilter.value() instanceof Collection<?> valueList) {
                List<String> inValues = valueList.stream().map(Object::toString).toList();
                completedSpecification = specification.and(generateInSpecification(resourceFilter.column(), inValues));
            } else {
                doLogWarn(resourceFilter);
            }
        } else {
            doLogWarn(resourceFilter);
        }

        return completedSpecification;
    }

    private void doLogWarn(ResourceFilterDTO resourceFilter) {
        log.warn(WARN_UNEXPECTED_TYPE_ENCOUNTERED_FOR, resourceFilter.column(), resourceFilter.type(), resourceFilter.dataType());
    }

    private <X> Specification<X> generateInSpecification(String column, List<String> inPossibleValues) {
        if (inPossibleValues.size() > CHUNK_SIZE) {
            List<List<String>> chunksOfInValues = Lists.partition(inPossibleValues, CHUNK_SIZE);
            Specification<X> containerSpec = null;

            for (List<String> chunk : chunksOfInValues) {
                Specification<X> multiOrEqualSpec = Specification.anyOf(in(column, chunk));
                if (containerSpec == null) {
                    containerSpec = multiOrEqualSpec;
                } else {
                    containerSpec = containerSpec.or(multiOrEqualSpec);
                }
            }

            return containerSpec;
        } else {
            return Specification.anyOf(in(column, inPossibleValues));
        }
    }

    public <X> Specification<X> in(String field, List<String> values) {
        return (root, cq, cb) ->
                getColumnPath(root, field).in(values);
    }

    private <X, Y> Path<Y> getColumnPath(Root<X> root, String dotSeparatedFields) {
        if (!dotSeparatedFields.contains(".")) {
            return root.get(dotSeparatedFields);
        } else {
            String[] fields = dotSeparatedFields.split("\\.");
            Path<Y> path = root.get(fields[0]);

            for (int i = 1; i < fields.length; ++i) {
                path = path.get(fields[i]);
            }

            return path;
        }
    }
}
