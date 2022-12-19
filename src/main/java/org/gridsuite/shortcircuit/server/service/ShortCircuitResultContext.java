/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Getter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Getter
public class ShortCircuitResultContext {

    private static final String REPORT_UUID = "reportUuid";

    public static final String VARIANT_ID = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    private final UUID resultUuid;

    private final ShortCircuitRunContext runContext;

    public ShortCircuitResultContext(UUID resultUuid, ShortCircuitRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    private static List<UUID> getHeaderList(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null || header.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(header.split(",")).stream()
            .map(UUID::fromString)
            .collect(Collectors.toList());
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    public static ShortCircuitResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        String variantId = (String) headers.get(VARIANT_ID);
        List<UUID> otherNetworkUuids = getHeaderList(headers, "otherNetworkUuids");

        String receiver = (String) headers.get("receiver");
        ShortCircuitParameters parameters;
        try {
            parameters = objectMapper.readValue(message.getPayload(), ShortCircuitParameters.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID) ? UUID.fromString((String) headers.get(REPORT_UUID)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        ShortCircuitRunContext runContext = new ShortCircuitRunContext(networkUuid,
            variantId, otherNetworkUuids, receiver,
            parameters, reportUuid, reporterId);
        return new ShortCircuitResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String parametersJson;
        try {
            parametersJson = objectMapper.writeValueAsString(runContext.getParameters());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(parametersJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader(VARIANT_ID, runContext.getVariantId())
                .setHeader("otherNetworkUuids", runContext.getOtherNetworkUuids().stream().map(UUID::toString).collect(Collectors.joining(",")))
                .setHeader("receiver", runContext.getReceiver())
                .setHeader(REPORT_UUID, Objects.toString(runContext.getReportUuid(), null))
                .setHeader(REPORTER_ID_HEADER, runContext.getReporterId())
                .build();
    }
}
