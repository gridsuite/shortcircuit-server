/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Data
public class ShortCircuitRunContext {
    private final UUID networkUuid;
    private final String variantId;
    private final String receiver;
    private final ShortCircuitParameters parameters;
    private final UUID reportUuid;
    private final String reporterId;
    private final String reportType;
    private final String userId;
    private final String busId;
    private Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
}
