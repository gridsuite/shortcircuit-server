/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * An object that can be used to filter data with the JPA Criteria API (via Spring Specification)
 * @param dataType the type of data we want to filter (text, number)
 * @param type the type of filter (contains, startsWith...)
 * @param value the value of the filter
 * @param column the column on which the filter will be applied
 * @param tolerance precision/tolerance used for the comparisons (simulates the rounding of the database values) Only useful for numbers.
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public record ResourceFilter(DataType dataType, Type type, Object value, String column, Double tolerance) {
    public ResourceFilter(DataType dataType, Type type, Object value, String column) {
        this(dataType, type, value, column, null);
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    public enum DataType {
        @JsonProperty("text")
        TEXT,
        @JsonProperty("number")
        NUMBER,
    }

    public enum Type {
        @JsonProperty("equals")
        EQUALS,
        @JsonProperty("contains")
        CONTAINS,
        @JsonProperty("startsWith")
        STARTS_WITH,
        @JsonProperty("notEqual")
        NOT_EQUAL,
        @JsonProperty("lessThanOrEqual")
        LESS_THAN_OR_EQUAL,
        @JsonProperty("greaterThanOrEqual")
        GREATER_THAN_OR_EQUAL
    }

    public static List<ResourceFilter> fromStringToList(String filters) throws JsonProcessingException {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        return objectMapper.readValue(filters, new TypeReference<>() {
        });
    }
}
