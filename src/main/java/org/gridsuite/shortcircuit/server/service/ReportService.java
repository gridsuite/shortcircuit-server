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
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ReportService {

    static final String REPORT_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private String reportServerBaseUri;

    @Autowired
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @Autowired
    public ReportService(ObjectMapper objectMapper,
                         @Value("${report-server.base-uri:http://report-server/}") String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
        this.objectMapper = objectMapper;
        ReporterModelJsonModule reporterModelJsonModule = new ReporterModelJsonModule();
        objectMapper.registerModule(reporterModelJsonModule);
    }

    public void setReportServerBaseUri(String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
    }

    private String getReportServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    public void sendReport(UUID reportUuid, Reporter reporter) {
        Objects.requireNonNull(reportUuid);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .buildAndExpand(reportUuid)
            .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(getReportServerURI() + path, HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reporter), headers), Reporter.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("Error sending report", error);
        }
    }
}
