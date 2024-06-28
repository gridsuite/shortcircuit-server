/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication(scanBasePackageClasses = { ShortCircuitApplication.class, NetworkStoreService.class, NotificationService.class })
public class ShortCircuitApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShortCircuitApplication.class, args);
    }
}
