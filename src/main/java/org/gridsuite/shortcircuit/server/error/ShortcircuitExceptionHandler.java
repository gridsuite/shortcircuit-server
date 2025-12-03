/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

@ControllerAdvice
public class ShortcircuitExceptionHandler extends AbstractBusinessExceptionHandler<ShortCircuitException, ShortcircuitBusinessErrorCode> {
    protected ShortcircuitExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull ShortcircuitBusinessErrorCode getBusinessCode(ShortCircuitException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(ShortcircuitBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case BUS_OUT_OF_VOLTAGE, INCONSISTENT_VOLTAGE_LEVELS, MISSING_EXTENSION_DATA ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(ShortCircuitException.class)
    protected ResponseEntity<PowsyblWsProblemDetail> handleShortcircuitException(
            ShortCircuitException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}
