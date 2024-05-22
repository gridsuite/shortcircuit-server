/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.computation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeJsonModule;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class ReportService {

    static final String REPORT_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private static final String QUERY_PARAM_REPORT_TYPE_FILTER = "reportTypeFilter";
    private static final String QUERY_PARAM_REPORT_THROW_ERROR = "errorOnReportNotFound";
    @Setter
    private String reportServerBaseUri;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public ReportService(ObjectMapper objectMapper,
                         @Value("${gridsuite.services.report-server.base-uri:http://report-server/}") String reportServerBaseUri,
                         RestTemplate restTemplate) {
        this.reportServerBaseUri = reportServerBaseUri;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        ReportNodeJsonModule reportNodeJsonModule = new ReportNodeJsonModule();
        objectMapper.registerModule(reportNodeJsonModule);
    }

    private String getReportServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    public void sendReport(UUID reportUuid, ReportNode reportNode) {
        Objects.requireNonNull(reportUuid);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .buildAndExpand(reportUuid)
            .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(getReportServerURI() + path, HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reportNode), headers), ReportNode.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("Error sending report", error);
        }
    }

    public void deleteReport(UUID reportUuid, String reportType) {
        Objects.requireNonNull(reportUuid);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
                .queryParam(QUERY_PARAM_REPORT_TYPE_FILTER, reportType)
                .queryParam(QUERY_PARAM_REPORT_THROW_ERROR, false)
                .buildAndExpand(reportUuid)
                .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(getReportServerURI() + path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
