/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The filter for a numeric field
 * @param type the type of filter (equals, lessThan...)
 * @param filter the value of the filter
 */
public record NumberFilter(NumberFilterType type, Double filter) {

    public enum NumberFilterType {
        @JsonProperty("notEqual")
        NOT_EQUAL,
        @JsonProperty("lessThanOrEqual")
        LESS_THAN_OR_EQUAL,
        @JsonProperty("greaterThanOrEqual")
        GREATER_THAN_OR_EQUAL
    }
}
