/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuitanalysis.server;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.Utils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
@ComponentScan(basePackageClasses = {ShortCircuitAnalysisApplication.class, NetworkStoreService.class})
public class ShortCircuitAnalysisApplication {

    public static void main(String[] args) {
        Utils.initProperties();
        SpringApplication.run(ShortCircuitAnalysisApplication.class, args);
    }
}
