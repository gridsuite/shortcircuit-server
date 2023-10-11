/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The filter for a text field
 * @param type the type of filter (contains, startsWith...)
 * @param filter the value of the filter
 */
public record TextFilter(TextFilterType type, String filter, @JsonIgnore String filterType) {

    public enum TextFilterType {
        @JsonProperty("contains")
        CONTAINS,
        @JsonProperty("startsWith")
        STARTS_WITH
    }
}
