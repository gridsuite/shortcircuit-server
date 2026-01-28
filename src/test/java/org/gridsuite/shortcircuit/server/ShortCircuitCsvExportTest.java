/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.shortcircuit.server.dto.CsvExportParams;
import org.gridsuite.shortcircuit.server.dto.FaultResultsMode;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.shortcircuit.server.TestUtils.unzip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@AutoConfigureMockMvc
@SpringBootTest
public class ShortCircuitCsvExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ShortCircuitService shortCircuitService;

    @Autowired
    private ObjectMapper mapper;

    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID RESULT_UUID_NOT_FOUND = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");

    private static final List<String> CSV_HEADERS = List.of(
            "ID nœud",
            "Type",
            "Départs",
            "Icc (kA)",
            "Type de limite",
            "Icc min (kA)",
            "IMACC (kA)",
            "Pcc (MVA)",
            "Icc - Icc min (kA)",
            "Icc - IMACC (kA)"
    );
    private final Map<String, String> enumTranslations = Map.of(
            "THREE_PHASE", "Triphasé",
            "SINGLE_PHASE", "Monophasé",
            "ACTIVE_POWER", "Puissance active",
            "APPARENT_POWER", "Puissance apparente",
            "CURRENT", "Intensité",
            "LOW_VOLTAGE", "Tension basse",
            "HIGH_VOLTAGE", "Tension haute",
            "LOW_SHORT_CIRCUIT_CURRENT", "Icc min",
            "HIGH_SHORT_CIRCUIT_CURRENT", "Icc max",
            "OTHER", "Autre"
    );

    @Test
    void test() throws Exception {
        doReturn(Page.empty()).when(shortCircuitService).getFaultResultsPage(nullable(UUID.class), nullable(String.class), eq(RESULT_UUID), nullable(FaultResultsMode.class), nullable(String.class), nullable(GlobalFilter.class), nullable(Pageable.class));
        doReturn(null).when(shortCircuitService).getFaultResultsPage(nullable(UUID.class), nullable(String.class), eq(RESULT_UUID_NOT_FOUND), nullable(FaultResultsMode.class), nullable(String.class), nullable(GlobalFilter.class), nullable(Pageable.class));
        MvcResult result;

        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS)
                                .enumValueTranslations(enumTranslations)
                                .language("en").build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        // test with result not found
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID_NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS)
                                .enumValueTranslations(enumTranslations)
                                .language("en").build())))
                .andExpectAll(status().isNotFound());

        // test with invalid csv export parameters : CsvExportParams, csvHeader or enumValueTranslations is null
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder().build())))
                .andExpectAll(status().isBadRequest());

        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS).build())))
                .andExpectAll(status().isBadRequest());

        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .enumValueTranslations(enumTranslations).build())))
                .andExpectAll(status().isBadRequest());

        // test on headers
        for (String language : List.of("fr", "en")) {
            result = mockMvc.perform(post(
                            "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(CsvExportParams.builder()
                                    .csvHeader(CSV_HEADERS)
                                    .enumValueTranslations(enumTranslations)
                                    .language(language).build())))
                    .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();
            byte[] zipFile = result.getResponse().getContentAsByteArray();
            byte[] unzippedCsvFile = unzip(zipFile);
            String unzippedCsvFileAsString = new String(unzippedCsvFile, StandardCharsets.UTF_8);
            List<String> actualCsvLines = List.of(Arrays.asList(unzippedCsvFileAsString.split("\n"))
                    .get(0).split(language.equals("fr") ? ";" : ","));
            // Including "\uFEFF" indicates the UTF-8 BOM at the start
            List<String> expectedLines = List.of(
                    "\uFEFFID nœud",
                    "Type",
                    "Départs",
                    "Icc (kA)",
                    "Type de limite",
                    "Icc min (kA)",
                    "IMACC (kA)",
                    "Pcc (MVA)",
                    "Icc - Icc min (kA)",
                    "Icc - IMACC (kA)"
            );
            assertEquals(expectedLines, actualCsvLines);
        }
    }
}
