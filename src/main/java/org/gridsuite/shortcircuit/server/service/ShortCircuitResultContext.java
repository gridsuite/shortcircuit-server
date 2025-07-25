/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;
import org.gridsuite.computation.service.AbstractResultContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.computation.service.NotificationService.*;
import static org.gridsuite.computation.utils.MessageUtils.getNonNullHeader;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitResultContext extends AbstractResultContext<ShortCircuitRunContext> {

    public static final String HEADER_BUS_ID = "busId";

    public ShortCircuitResultContext(UUID resultUuid, ShortCircuitRunContext runContext) {
        super(resultUuid, runContext);
    }

    public static ShortCircuitResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, HEADER_RESULT_UUID));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, NETWORK_UUID_HEADER));
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String provider = (String) headers.get(HEADER_PROVIDER);
        String userId = (String) headers.get(HEADER_USER_ID);
        String busId = (String) headers.get(HEADER_BUS_ID);

        ShortCircuitParameters parameters;
        try {
            parameters = objectMapper.readValue(message.getPayload(), ShortCircuitParameters.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        ShortCircuitRunContext runContext = new ShortCircuitRunContext(networkUuid, variantId, receiver, parameters,
                reportUuid, reporterId, reportType, userId, provider, busId);
        return new ShortCircuitResultContext(resultUuid, runContext);
    }

    @Override
    protected Map<String, String> getSpecificMsgHeaders(ObjectMapper objectMapper) {
        if (getRunContext().getBusId() != null) {
            return Map.of(HEADER_BUS_ID, getRunContext().getBusId());
        } else {
            return super.getSpecificMsgHeaders(objectMapper);
        }
    }
}
