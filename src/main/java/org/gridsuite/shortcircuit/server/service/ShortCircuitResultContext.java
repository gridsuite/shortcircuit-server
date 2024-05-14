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
import org.gridsuite.shortcircuit.server.computation.service.AbstractResultContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.*;

import static org.gridsuite.shortcircuit.server.computation.service.NotificationService.*;
import static org.gridsuite.shortcircuit.server.computation.utils.MessageUtils.getNonNullHeader;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitResultContext extends AbstractResultContext<ShortCircuitRunContext> {

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
        String userId = (String) headers.get(HEADER_USER_ID);
        String busId = (String) headers.get(HEADER_BUS_ID);

        ShortCircuitParameters parameters;
        try {
            // can't use the following line because jackson doesn't play well with null..?
            // parameters = objectMapper.reader().withRootName(MESSAGE_ROOT_NAME).readValue(message.getPayload(), LoadFlowParametersInfos.class);
            parameters = objectMapper.treeToValue(objectMapper.readTree(message.getPayload()).get(MESSAGE_ROOT_NAME), ShortCircuitParameters.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        ShortCircuitRunContext runContext = new ShortCircuitRunContext(networkUuid, variantId, receiver, parameters,
                reportUuid, reporterId, reportType, userId, busId);
        return new ShortCircuitResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String parametersJson;
        try {
            // can't use the following line because jackson doesn't play well with null..?
            // parametersJson = objectMapper.writer().withRootName(MESSAGE_ROOT_NAME).writeValueAsString(runContext.getParameters());
            parametersJson = objectMapper.writeValueAsString(objectMapper.createObjectNode().putPOJO(MESSAGE_ROOT_NAME, runContext.getParameters()));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(parametersJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportInfos().reportUuid() != null ? runContext.getReportInfos().reportUuid().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReportInfos().reporterId())
                .setHeader(REPORT_TYPE_HEADER, runContext.getReportInfos().computationType())
                .setHeader(HEADER_BUS_ID, runContext.getBusId())
                .build();
    }
}
