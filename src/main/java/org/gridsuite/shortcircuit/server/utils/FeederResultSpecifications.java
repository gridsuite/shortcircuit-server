/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.util.List;
import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public final class FeederResultSpecifications {

    // Utility class, so no constructor
    private FeederResultSpecifications() {
    }

    public static Specification<FeederResultEntity> resultUuidEquals(UUID value) {
        return (feederResult, cq, cb) -> cb.equal(feederResult.get("faultResult").get("result").get("resultUuid"), value);
    }

    public static Specification<FeederResultEntity> contains(String field, String value) {
        return (feederResult, cq, cb) -> cb.like(getColumnPath(feederResult, field), "%" + EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static Specification<FeederResultEntity> startsWith(String field, String value) {
        return (feederResult, cq, cb) -> cb.like(getColumnPath(feederResult, field), EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static Specification<FeederResultEntity> notEqual(String field, Double value) {
        return (feederResult, cq, cb) -> cb.notEqual(getColumnPath(feederResult, field), value);
    }

    public static Specification<FeederResultEntity> lessThanOrEqual(String field, Double value) {
        return (feederResult, cq, cb) -> cb.lessThanOrEqualTo(getColumnPath(feederResult, field), value);
    }

    public static Specification<FeederResultEntity> greaterThanOrEqual(String field, Double value) {
        return (feederResult, cq, cb) -> cb.greaterThanOrEqualTo(getColumnPath(feederResult, field), value);
    }

    public static Specification<FeederResultEntity> buildSpecification(UUID resultUuid, List<ResourceFilter> resourceFilters) {
        Specification<FeederResultEntity> specification = Specification.where(resultUuidEquals(resultUuid));

        if (resourceFilters == null || resourceFilters.isEmpty()) {
            return specification;
        }

        for (ResourceFilter resourceFilter : resourceFilters) {
            if (resourceFilter.dataType() == ResourceFilter.DataType.TEXT) {
                String value = resourceFilter.value();
                switch (resourceFilter.type()) {
                    case CONTAINS ->
                            specification = specification.and(contains(resourceFilter.field(), value));
                    case STARTS_WITH ->
                            specification = specification.and(startsWith(resourceFilter.field(), value));
                }
            }
            if (resourceFilter.dataType() == ResourceFilter.DataType.NUMBER) {
                Double value = Double.valueOf(resourceFilter.value());
                switch (resourceFilter.type()) {
                    case NOT_EQUAL ->
                            specification = specification.and(notEqual(resourceFilter.field(), value));
                    case LESS_THAN_OR_EQUAL ->
                            specification = specification.and(lessThanOrEqual(resourceFilter.field(), value));
                    case GREATER_THAN_OR_EQUAL ->
                            specification = specification.and(greaterThanOrEqual(resourceFilter.field(), value));
                }
            }
        }

        return specification;
    }

    /**
     * This method allow to query eventually dot separated fields with the Criteria API
     * Ex : from 'fortescueCurrent.positiveMagnitude' we create the query path
     * root.get("fortescueCurrent").get("positiveMagnitude") to access to the correct nested field
     * @param root the root entity
     * @param dotSeparatedFields dot separated fields (can be only one field without any dot)
     * @return path for the query
     */
    public static Path getColumnPath(Root root, String dotSeparatedFields) {
        if (dotSeparatedFields.contains(".")) {
            String[] fields = dotSeparatedFields.split("\\.");
            Path path = root;
            for (String field : fields) {
                path = path.get(field);
            }
            return path;
        } else {
            return root.get(dotSeparatedFields);
        }
    }
}
