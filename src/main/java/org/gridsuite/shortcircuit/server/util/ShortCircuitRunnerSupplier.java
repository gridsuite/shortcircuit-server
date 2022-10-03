/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.util;

import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Etienne Homer <etienne.homerr at rte-france.com>
 */
@Service
public class ShortCircuitRunnerSupplier {
    @Value("${shortcircuit.default-provider}")
    private String defaultProvider;

    public ShortCircuitAnalysis.Runner getRunner(String provider) {
        return ShortCircuitAnalysis.find(provider != null ? provider : defaultProvider);
    }
}
