/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server;

import com.powsybl.ws.commons.error.AbstractBaseRestExceptionHandler;
import com.powsybl.ws.commons.error.AbstractBusinessException;
import com.powsybl.ws.commons.error.BusinessErrorCode;
import com.powsybl.ws.commons.error.ServerNameProvider;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.gridsuite.computation.ComputationBusinessErrorCode.*;
import static org.gridsuite.shortcircuit.server.ShortcircuitBusinessErrorCode.*;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractBaseRestExceptionHandler<AbstractBusinessException, BusinessErrorCode> {

    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull BusinessErrorCode getBusinessCode(AbstractBusinessException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(BusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case RESULT_NOT_FOUND, NETWORK_NOT_FOUND, PARAMETERS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_FILTER_FORMAT,
                 INVALID_SORT_FORMAT,
                 INVALID_FILTER,
                 INVALID_EXPORT_PARAMS -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
