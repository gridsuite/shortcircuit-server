/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FeederResult {
    public static final String CONNECTABLE_ID_COL = "connectableId";

    private String connectableId;

    private double current;

    private double positiveMagnitude;
}
