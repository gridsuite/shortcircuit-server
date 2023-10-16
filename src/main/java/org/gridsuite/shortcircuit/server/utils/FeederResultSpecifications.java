/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import org.gridsuite.shortcircuit.server.dto.Filter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public final class FeederResultSpecifications {

    // Utility class, so no constructor
    private FeederResultSpecifications() {
    }

    public static Specification<FeederResultEntity> resultUuidEquals(UUID value) {
        return (feederResult, cq, cb) -> cb.equal(feederResult.get("faultResult").get("result").get("resultUuid"), value);
    }

    public static Specification<FeederResultEntity> contains(String column, String value) {
        return (feederResult, cq, cb) -> cb.like(feederResult.get(column), "%" + value + "%");
    }

    public static Specification<FeederResultEntity> startsWith(String column, String value) {
        return (feederResult, cq, cb) -> cb.like(feederResult.get(column), value + "%");
    }

    public static Specification<FeederResultEntity> notEqual(String column, Double value) {
        return (feederResult, cq, cb) -> cb.notEqual(feederResult.get(column), value);
    }

    public static Specification<FeederResultEntity> lessThanOrEqualTo(String column, Double value) {
        return (feederResult, cq, cb) -> cb.lessThanOrEqualTo(feederResult.get(column), value);
    }

    public static Specification<FeederResultEntity> greaterThanOrEqualTo(String column, Double value) {
        return (feederResult, cq, cb) -> cb.greaterThanOrEqualTo(feederResult.get(column), value);
    }

    public static Specification<FeederResultEntity> buildSpecification(ShortCircuitAnalysisResultEntity resultEntity, List<Filter> filters) {
        Specification<FeederResultEntity> specification = Specification.where(resultUuidEquals(resultEntity.getResultUuid()));

        if (filters == null || filters.isEmpty()) {
            return specification;
        }

        for (Filter filter : filters) {
            if (filter.dataType() == Filter.DataType.TEXT) {
                String value = filter.value().toString();
                switch (filter.type()) {
                    case CONTAINS ->
                            specification = specification.and(contains(filter.column(), value));
                    case STARTS_WITH ->
                            specification = specification.and(startsWith(filter.column(), value));
                }
            }
            if (filter.dataType() == Filter.DataType.NUMBER) {
                Double value = Double.valueOf(filter.value().toString());
                switch (filter.type()) {
                    case NOT_EQUAL ->
                            specification = specification.and(notEqual(filter.column(), value));
                    case LESS_THAN_OR_EQUAL ->
                            specification = specification.and(lessThanOrEqualTo(filter.column(), value));
                    case GREATER_THAN_OR_EQUAL ->
                            specification = specification.and(greaterThanOrEqualTo(filter.column(), value));
                }
            }
        }

        return specification;
    }
}
