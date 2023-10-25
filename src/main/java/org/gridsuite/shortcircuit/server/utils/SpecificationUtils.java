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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.util.Collection;
import java.util.List;

import static org.springframework.data.jpa.domain.Specification.anyOf;
import static org.springframework.data.jpa.domain.Specification.not;

public final class SpecificationUtils {

    // Utility class, so no constructor
    private SpecificationUtils() {
    }

    // we use .as(String.class) to be able to works on enum fields
    public static <X> Specification<X> equals(String field, String value) {
        return (root, cq, cb) -> cb.equal(getColumnPath(root, field).as(String.class), value);
    }

    public static <X> Specification<X> contains(String field, String value) {
        return (root, cq, cb) -> cb.like(getColumnPath(root, field), "%" + EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static <X> Specification<X> startsWith(String field, String value) {
        return (root, cq, cb) -> cb.like(getColumnPath(root, field), EscapeCharacter.DEFAULT.escape(value) + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static <X> Specification<X> notEqual(String field, Double value) {
        return (root, cq, cb) -> cb.notEqual(getColumnPath(root, field), value);
    }

    public static <X> Specification<X> lessThanOrEqual(String field, Double value) {
        return (root, cq, cb) -> cb.lessThanOrEqualTo(getColumnPath(root, field), value);
    }

    public static <X> Specification<X> greaterThanOrEqual(String field, Double value) {
        return (root, cq, cb) -> cb.greaterThanOrEqualTo(getColumnPath(root, field), value);
    }

    public static <X> Specification<X> greaterThan(String field, Double value) {
        return (root, cq, cb) -> cb.greaterThan(getColumnPath(root, field), value);
    }

    public static <X> Specification<X> appendFiltersToSpecification(Specification<X> specification, List<ResourceFilter> resourceFilters) {

        if (resourceFilters == null || resourceFilters.isEmpty()) {
            return specification;
        }

        Specification<X> completedSpecification = specification;

        for (ResourceFilter resourceFilter : resourceFilters) {
            if (resourceFilter.dataType() == ResourceFilter.DataType.TEXT) {
                switch (resourceFilter.type()) {
                    case EQUALS -> {
                        // THIS IS SUPPOSED TO BE TEMPORARY (waiting for new filters from front end)
                        if (resourceFilter.value() instanceof Collection<?> valueList) {
                            // ideally, we should be able to manage the OR condition everywhere and use a list of single equals filters from the frond end
                            // it's done like this in sensi so I did the same by coherence, but it should be temporary
                            Specification<X> anyOfSpec = anyOf(valueList.stream().map(value -> SpecificationUtils.<X>equals(resourceFilter.field(), value.toString())).toList());
                            completedSpecification = completedSpecification.and(anyOfSpec);
                        } else if (resourceFilter.value() == null) {
                            // if the value is null, we build an impossible specification
                            completedSpecification = completedSpecification.and(not(completedSpecification));
                        } else {
                            String value = resourceFilter.value().toString();
                            completedSpecification = completedSpecification.and(equals(resourceFilter.field(), value));
                        }
                    }
                    case CONTAINS -> {
                        String value = resourceFilter.value().toString();
                        completedSpecification = completedSpecification.and(contains(resourceFilter.field(), value));
                    }
                    case STARTS_WITH -> {
                        String value = resourceFilter.value().toString();
                        completedSpecification = completedSpecification.and(startsWith(resourceFilter.field(), value));
                    }
                }
            }
            if (resourceFilter.dataType() == ResourceFilter.DataType.NUMBER) {
                // We need to cast it as String before and use .valueOf to be able to works with integers
                Double value = Double.valueOf(resourceFilter.value().toString());
                switch (resourceFilter.type()) {
                    case NOT_EQUAL ->
                        completedSpecification = completedSpecification.and(notEqual(resourceFilter.field(), value));
                    case LESS_THAN_OR_EQUAL ->
                        completedSpecification = completedSpecification.and(lessThanOrEqual(resourceFilter.field(), value));
                    case GREATER_THAN_OR_EQUAL ->
                        completedSpecification = completedSpecification.and(greaterThanOrEqual(resourceFilter.field(), value));
                }
            }
        }

        return completedSpecification;
    }

    /**
     * This method allow to query eventually dot separated fields with the Criteria API
     * Ex : from 'fortescueCurrent.positiveMagnitude' we create the query path
     * root.get("fortescueCurrent").get("positiveMagnitude") to access to the correct nested field
     *
     * @param root               the root entity
     * @param dotSeparatedFields dot separated fields (can be only one field without any dot)
     * @param <X>                the entity type referenced by the root
     * @param <Y>                the type referenced by the path
     * @return path for the query
     */
    public static <X, Y> Path<Y> getColumnPath(Root<X> root, String dotSeparatedFields) {
        if (dotSeparatedFields.contains(".")) {
            String[] fields = dotSeparatedFields.split("\\.");
            Path<Y> path = root.get(fields[0]);
            for (int i = 1; i < fields.length; i++) {
                path = path.get(fields[i]);
            }
            return path;
        } else {
            return root.get(dotSeparatedFields);
        }
    }
}
