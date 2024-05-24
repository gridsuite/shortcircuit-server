/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories.specifications;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.jpa.domain.Specification.anyOf;
import static org.springframework.data.jpa.domain.Specification.not;

/**
 * Utility class to create Spring Data JPA Specification (Spring interface for JPA Criteria API).
 *
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public final class SpecificationUtils {

    // Utility class, so no constructor
    private SpecificationUtils() {
    }

    // we use .as(String.class) to be able to works on enum fields
    public static <X> Specification<X> equals(String field, String value) {
        return (root, cq, cb) -> cb.equal(cb.upper(getColumnPath(root, field)).as(String.class), value.toUpperCase());
    }

    public static <X> Specification<X> contains(String field, String value) {
        return (root, cq, cb) -> cb.like(cb.upper(getColumnPath(root, field)), "%" + EscapeCharacter.DEFAULT.escape(value).toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static <X> Specification<X> startsWith(String field, String value) {
        return (root, cq, cb) -> cb.like(cb.upper(getColumnPath(root, field)), EscapeCharacter.DEFAULT.escape(value).toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    /**
     * Returns a specification where the field value is not equal within the given tolerance.
     *
     * @param <X> Entity type.
     * @param field Field name.
     * @param value Comparison value.
     * @param tolerance Tolerance range.
     * @return Specification of non-equality with tolerance.
     */
    public static <X> Specification<X> notEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            return cb.or(
                    cb.greaterThan(doubleExpression, value + tolerance),
                    cb.lessThan(doubleExpression, value - tolerance)
            );
        };
    }

    /**
     * Returns a specification where the field value is less than or equal to the value plus tolerance.
     *
     * @param <X> Entity type.
     * @param field Field name.
     * @param value Comparison value.
     * @param tolerance Tolerance range.
     * @return Specification of less than or equal with tolerance.
     */
    public static <X> Specification<X> lessThanOrEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            return cb.lessThanOrEqualTo(doubleExpression, value + tolerance);
        };
    }

    /**
     * Returns a specification where the field value is greater than or equal to the value minus tolerance.
     *
     * @param <X> Entity type.
     * @param field Field name.
     * @param value Comparison value.
     * @param tolerance Tolerance range.
     * @return Specification of greater than or equal with tolerance.
     */
    public static <X> Specification<X> greaterThanOrEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            return cb.greaterThanOrEqualTo(doubleExpression, value - tolerance);
        };
    }

    public static <X> Specification<X> isNotEmpty(String field) {
        return (root, cq, cb) -> cb.isNotEmpty(getColumnPath(root, field));
    }

    public static <X> Specification<X> distinct() {
        return (root, cq, cb) -> {
            cq.distinct(true);
            return null;
        };
    }

    public static <X> Specification<X> appendFiltersToSpecification(Specification<X> specification, List<ResourceFilter> resourceFilters) {
        Objects.requireNonNull(specification);

        if (resourceFilters == null || resourceFilters.isEmpty()) {
            return specification;
        }

        Specification<X> completedSpecification = specification;

        for (ResourceFilter resourceFilter : resourceFilters) {
            if (resourceFilter.dataType() == ResourceFilter.DataType.TEXT) {
                completedSpecification = appendTextFilterToSpecification(completedSpecification, resourceFilter);
            } else if (resourceFilter.dataType() == ResourceFilter.DataType.NUMBER) {
                completedSpecification = appendNumberFilterToSpecification(completedSpecification, resourceFilter);
            }
        }

        return completedSpecification;
    }

    @NotNull
    private static <X> Specification<X> appendTextFilterToSpecification(Specification<X> specification, ResourceFilter resourceFilter) {
        Specification<X> completedSpecification = specification;

        switch (resourceFilter.type()) {
            case EQUALS -> {
                // this type can manage one value or a list of values (with OR)
                if (resourceFilter.value() instanceof Collection<?> valueList) {
                    completedSpecification = completedSpecification.and(anyOf(valueList.stream().map(value -> SpecificationUtils.<X>equals(resourceFilter.column(), value.toString())).toList()));
                } else if (resourceFilter.value() == null) {
                    // if the value is null, we build an impossible specification (trick to remove later on ?)
                    completedSpecification = completedSpecification.and(not(completedSpecification));
                } else {
                    completedSpecification = completedSpecification.and(equals(resourceFilter.column(), resourceFilter.value().toString()));
                }
            }
            case CONTAINS ->
                completedSpecification = completedSpecification.and(contains(resourceFilter.column(), resourceFilter.value().toString()));
            case STARTS_WITH ->
                completedSpecification = completedSpecification.and(startsWith(resourceFilter.column(), resourceFilter.value().toString()));
            default -> throwBadFilterTypeException(resourceFilter.type(), resourceFilter.dataType());
        }

        return completedSpecification;
    }

    @NotNull
    private static <X> Specification<X> appendNumberFilterToSpecification(Specification<X> specification, ResourceFilter resourceFilter) {
        final double tolerence = 0.00001; // tolerance for comparison
        String value = resourceFilter.value().toString();
        return createNumberPredicate(specification, resourceFilter, value, tolerence);
    }

    private static <X> Specification<X> createNumberPredicate(Specification<X> specification, ResourceFilter resourceFilter, String value, double tolerance) {
        Specification<X> completedSpecification = specification;
        Double valueDouble = Double.valueOf(value);
        switch (resourceFilter.type()) {
            case NOT_EQUAL ->
                    completedSpecification = specification.and(notEqual(resourceFilter.column(), valueDouble, tolerance));
            case LESS_THAN_OR_EQUAL ->
                    completedSpecification = specification.and(lessThanOrEqual(resourceFilter.column(), valueDouble, tolerance));
            case GREATER_THAN_OR_EQUAL ->
                    completedSpecification = specification.and(greaterThanOrEqual(resourceFilter.column(), valueDouble, tolerance));
            default -> throwBadFilterTypeException(resourceFilter.type(), resourceFilter.dataType());
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
    private static <X, Y> Path<Y> getColumnPath(Root<X> root, String dotSeparatedFields) {
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

    private static void throwBadFilterTypeException(ResourceFilter.Type filterType, ResourceFilter.DataType dataType) throws IllegalArgumentException {
        throw new IllegalArgumentException("The filter type " + filterType + " is not supported with the data type " + dataType);
    }
}
