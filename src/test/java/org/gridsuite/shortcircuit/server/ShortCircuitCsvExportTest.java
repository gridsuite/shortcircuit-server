/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static java.lang.Double.NaN;
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

    private static final Fault FAULT_1 = new Fault("faultId1", "faultElementId1", "faultVoltageLevelId1", "faultType1");
    private static final Fault FAULT_2 = new Fault("faultId2", "faultElementId2", "faultVoltageLevelId2", "faultType2");
    private static final ShortCircuitLimits LIMITS = new ShortCircuitLimits(10.5, 200, 34.8, -154.7);
    private static final FaultResult FAULT_RESULT_1 = new FaultResult(FAULT_1, 50, NaN, 20, List.of(), List.of(), LIMITS);
    private static final FaultResult FAULT_RESULT_2 = new FaultResult(FAULT_2, 40, NaN, 10, List.of(), List.of(), LIMITS);
    private static final List<FaultResult> FAULT_RESULTS = List.of(FAULT_RESULT_1, FAULT_RESULT_2);
    private static final List<FaultResult> FAULT_RESULTS_FILTERED_AND_SORTED = List.of(FAULT_RESULT_1);

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
    private static final Sort SORT = Sort.by("property");
    private static final String FILTERS = "filters";
    private static final String GLOBAL_FILTERS = "globalFilters";

    @Test
    void test() throws Exception {
        Page<FaultResult> page = new PageImpl<>(FAULT_RESULTS, Pageable.unpaged(), FAULT_RESULTS.size());
        Page<FaultResult> pageFilteredAndSorted = new PageImpl<>(FAULT_RESULTS_FILTERED_AND_SORTED, Pageable.unpaged(), FAULT_RESULTS_FILTERED_AND_SORTED.size());

        doReturn(page).when(shortCircuitService).getFaultResultsPage(nullable(UUID.class), nullable(String.class), eq(RESULT_UUID), nullable(FaultResultsMode.class), nullable(String.class), nullable(String.class), nullable(Pageable.class));
        doReturn(pageFilteredAndSorted).when(shortCircuitService).getFaultResultsPage(nullable(UUID.class), nullable(String.class), eq(RESULT_UUID), nullable(FaultResultsMode.class), eq(FILTERS), eq(GLOBAL_FILTERS), eq(Pageable.unpaged(SORT)));
        doReturn(null).when(shortCircuitService).getFaultResultsPage(nullable(UUID.class), nullable(String.class), eq(RESULT_UUID_NOT_FOUND), nullable(FaultResultsMode.class), nullable(String.class), nullable(String.class), nullable(Pageable.class));
        MvcResult result;

        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS)
                                .enumValueTranslations(enumTranslations)
                                .language("en").build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        // test with filters and sort parameters
        mockMvc.perform(post(
                        "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .param("filters", FILTERS)
                        .param("globalFilters", GLOBAL_FILTERS)
                        .param("sort", SORT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS)
                                .enumValueTranslations(enumTranslations)
                                .language("en").build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        for (String language : List.of("fr", "en")) {
            // tester le contenu aussi (addrows), avec filters, globalFilters, sort
            result = mockMvc.perform(post(
                            "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                            .param("filters", FILTERS)
                            .param("globalFilters", GLOBAL_FILTERS)
                            .param("sort", SORT.toString())
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
            List<List<String>> actualCsv = Arrays.asList(unzippedCsvFileAsString.split("\n"))
                    .stream().map(line -> List.of(line.split(language.equals("fr") ? ";" : ","))).toList();
            // Including "\uFEFF" indicates the UTF-8 BOM at the start
            List<String> expectedHeaders = List.of(
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
            assertEquals(expectedHeaders, actualCsv.getFirst());
            // there should be 5 lines : header + 2 fault results + 2 empty lines
            assertEquals(5, actualCsv.size());
        }

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
    }
}
