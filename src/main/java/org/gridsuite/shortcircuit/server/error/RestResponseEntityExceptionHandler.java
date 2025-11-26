/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.error;

import com.powsybl.ws.commons.error.ServerNameProvider;
import org.gridsuite.computation.error.TypedComputationRestResponseEntityExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends TypedComputationRestResponseEntityExceptionHandler<ShortcircuitBusinessErrorCode> {
    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider, ShortcircuitBusinessErrorCode.class);
    }

    @Override
    protected HttpStatus mapSpecificStatus(ShortcircuitBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case BUS_OUT_OF_VOLTAGE, INCONSISTENT_VOLTAGE_LEVELS, MISSING_EXTENSION_DATA ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
