/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

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
        return (feederResult, cq, cb) -> cb.like(feederResult.get(column), "%" + EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static Specification<FeederResultEntity> startsWith(String column, String value) {
        return (feederResult, cq, cb) -> cb.like(feederResult.get(column), EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static Specification<FeederResultEntity> notEqual(String column, Double value) {
        return (feederResult, cq, cb) -> cb.notEqual(feederResult.get(column), value);
    }

    public static Specification<FeederResultEntity> lessThanOrEqual(String column, Double value) {
        return (feederResult, cq, cb) -> cb.lessThanOrEqualTo(feederResult.get(column), value);
    }

    public static Specification<FeederResultEntity> greaterThanOrEqual(String column, Double value) {
        return (feederResult, cq, cb) -> cb.greaterThanOrEqualTo(feederResult.get(column), value);
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
                            specification = specification.and(contains(resourceFilter.column(), value));
                    case STARTS_WITH ->
                            specification = specification.and(startsWith(resourceFilter.column(), value));
                }
            }
            if (resourceFilter.dataType() == ResourceFilter.DataType.NUMBER) {
                Double value = Double.valueOf(resourceFilter.value());
                switch (resourceFilter.type()) {
                    case NOT_EQUAL ->
                            specification = specification.and(notEqual(resourceFilter.column(), value));
                    case LESS_THAN_OR_EQUAL ->
                            specification = specification.and(lessThanOrEqual(resourceFilter.column(), value));
                    case GREATER_THAN_OR_EQUAL ->
                            specification = specification.and(greaterThanOrEqual(resourceFilter.column(), value));
                }
            }
        }

        return specification;
    }
}
