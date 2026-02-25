/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import lombok.*;

import java.util.UUID;

/**
 * Representation of one filter
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FilterElements {
    private UUID filterId;
    private String filterName; // TODO remove, we should retrieve filter name in front app
}
