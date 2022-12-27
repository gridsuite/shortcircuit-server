/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@AllArgsConstructor
public class FaultResult {

    private Fault fault;

    private List<LimitViolation> limitViolations;

    private List<FeederResult> feederResults;
}
