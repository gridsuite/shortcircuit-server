/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.computation.error.ComputationException;
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
import java.util.*;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static java.lang.Double.NaN;
import static org.gridsuite.shortcircuit.server.TestUtils.unzip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@AutoConfigureMockMvc
@SpringBootTest
class ShortCircuitCsvExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ShortCircuitService shortCircuitService;

    @Autowired
    private ObjectMapper mapper;

    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID RESULT_UUID_NOT_FOUND = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final String VARIANT_ID = "variantId";

    private static final Fault FAULT_1 = new Fault("faultId1", "faultElementId1", "faultVoltageLevelId1", "faultType1");
    private static final Fault FAULT_2 = new Fault("faultId2", "faultElementId2", "faultVoltageLevelId2", "faultType2");
    private static final FeederResult FEEDER_RESULT = new FeederResult("connectableId", 50, NaN, "");
    private static final ShortCircuitLimits LIMITS = new ShortCircuitLimits(10.5, 200, 34.8, -154.7);
    private static final FaultResult FAULT_RESULT_1 = new FaultResult(FAULT_1, 50, NaN, 20, List.of(), List.of(), LIMITS);
    private static final FaultResult FAULT_RESULT_2 = new FaultResult(FAULT_2, 40, NaN, 10, List.of(), List.of(), LIMITS);
    private static final FaultResult FAULT_RESULT_3 = new FaultResult(FAULT_1, 50, NaN, 20, List.of(), List.of(FEEDER_RESULT), LIMITS);

    private static final List<String> CSV_HEADERS_ALL_BUSES = List.of(
            "Nœud électrique",
            "Poste",
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
    private static final List<String> CSV_HEADERS_ONE_BUS = List.of(
            "Nœud électrique",
            "Poste",
            "Type",
            "Départs",
            "Icc (kA)",
            "Côté",
            "Type de limite",
            "Icc min (kA)",
            "IMACC (kA)",
            "Pcc (MVA)",
            "Icc - Icc min (kA)",
            "Icc - IMACC (kA)"
    );
    private static final Map<String, String> ENUM_TRANSLATIONS = Map.of(
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
    private static final String SORT = "sortField";
    private static final String FILTERS = "[{\"filter\": \"\"}]";
    private static final String GLOBAL_FILTERS = "{\"globalFilter\": []}";

    @Test
    void runAllBusesTest() throws Exception {
        doReturn(Page.empty()).when(shortCircuitService).getFaultResultsPage(null, null, RESULT_UUID, FaultResultsMode.FULL, null, null, Pageable.unpaged());
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ALL_BUSES)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en").build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        verify(shortCircuitService, times(1)).getFaultResultsPage(null, null, RESULT_UUID, FaultResultsMode.FULL, null, null, Pageable.unpaged());

        // test with filters and sort parameters
        doReturn(Page.empty()).when(shortCircuitService).getFaultResultsPage(NETWORK_UUID, VARIANT_ID, RESULT_UUID, FaultResultsMode.FULL, FILTERS, GLOBAL_FILTERS, Pageable.unpaged(Sort.by(SORT)));
        mockMvc.perform(post(
                        "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .param("networkUuid", NETWORK_UUID.toString())
                        .param("variantId", VARIANT_ID)
                        .param("filters", FILTERS)
                        .param("globalFilters", GLOBAL_FILTERS)
                        .param("sort", SORT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ALL_BUSES)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en").build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        verify(shortCircuitService, times(1)).getFaultResultsPage(NETWORK_UUID, VARIANT_ID, RESULT_UUID, FaultResultsMode.FULL, FILTERS, GLOBAL_FILTERS, Pageable.unpaged(Sort.by(SORT)));

        // test with result not found
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID_NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ALL_BUSES)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en").build())))
                .andExpect(result ->
                        assertInstanceOf(ComputationException.class, result.getResolvedException()));
        verify(shortCircuitService, times(1)).getFaultResultsPage(null, null, RESULT_UUID_NOT_FOUND, FaultResultsMode.FULL, null, null, Pageable.unpaged());
    }

    @Test
    void runOneBusTest() throws Exception {
        doReturn(FAULT_RESULT_1).when(shortCircuitService).getOneBusFaultResult(RESULT_UUID, null, Sort.unsorted());
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ONE_BUS)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en")
                                .oneBusCase(true)
                                .build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        verify(shortCircuitService, times(1)).getOneBusFaultResult(RESULT_UUID, null, Sort.unsorted());

        // test with filters and sort parameters
        doReturn(FAULT_RESULT_1).when(shortCircuitService).getOneBusFaultResult(RESULT_UUID, FILTERS, Sort.by(SORT));
        mockMvc.perform(post(
                        "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .param("filters", FILTERS)
                        .param("sort", SORT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ONE_BUS)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en")
                                .oneBusCase(true)
                                .build())))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        verify(shortCircuitService, times(1)).getOneBusFaultResult(RESULT_UUID, FILTERS, Sort.by(SORT));

        // test with result not found
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID_NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ONE_BUS)
                                .enumValueTranslations(ENUM_TRANSLATIONS)
                                .language("en")
                                .oneBusCase(true)
                                .build())))
                .andExpect(result ->
                        assertInstanceOf(ComputationException.class, result.getResolvedException()));
        verify(shortCircuitService, times(1)).getOneBusFaultResult(RESULT_UUID_NOT_FOUND, null, Sort.unsorted());
    }

    @Test
    void runWithInvalidCsvExportParametersTest() throws Exception {
        doReturn(Page.empty()).when(shortCircuitService).getFaultResultsPage(null, null, RESULT_UUID, FaultResultsMode.FULL, null, null, Pageable.unpaged());

        // test with empty CsvExportParams
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder().build())))
                .andExpectAll(status().isBadRequest());

        // test with null enumValueTranslations
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .csvHeader(CSV_HEADERS_ALL_BUSES).build())))
                .andExpectAll(status().isBadRequest());

        // test with null csvHeader
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CsvExportParams.builder()
                                .enumValueTranslations(ENUM_TRANSLATIONS).build())))
                .andExpectAll(status().isBadRequest());
    }

    @Test
    void oneBusResultTest() throws Exception {
        // Including "\uFEFF" indicates the UTF-8 BOM at the start
        List<String> expectedHeaders = List.of(
                "\uFEFFNœud électrique",
                "Poste",
                "Type",
                "Départs",
                "Icc (kA)",
                "Côté",
                "Type de limite",
                "Icc min (kA)",
                "IMACC (kA)",
                "Pcc (MVA)",
                "Icc - Icc min (kA)",
                "Icc - IMACC (kA)"
        );
        List<String> expectedLine1 = List.of("faultId1", "faultVoltageLevelId1", "", "", "", "", "", "0.011", "0.2", "20", "0.035", "-0.155");
        List<String> expectedLine2 = List.of("faultId1", "", "", "connectableId");

        doReturn(FAULT_RESULT_1).when(shortCircuitService).getOneBusFaultResult(RESULT_UUID, null, Sort.unsorted());
        resultTest(RESULT_UUID, true, List.of(expectedHeaders, expectedLine1));

        doReturn(FAULT_RESULT_3).when(shortCircuitService).getOneBusFaultResult(OTHER_RESULT_UUID, null, Sort.unsorted());
        resultTest(OTHER_RESULT_UUID, true, List.of(expectedHeaders, expectedLine1, expectedLine2));
    }

    @Test
    void allBusesResultTest() throws Exception {
        // Including "\uFEFF" indicates the UTF-8 BOM at the start
        List<String> expectedHeaders = List.of(
                "\uFEFFNœud électrique",
                "Poste",
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
        List<String> expectedLine1 = List.of("faultId1", "faultVoltageLevelId1", "", "", "0.05", "", "0.011", "0.2", "20", "0.035", "-0.155");
        List<String> expectedLine2 = List.of("faultId2", "faultVoltageLevelId2", "", "", "0.04", "", "0.011", "0.2", "10", "0.035", "-0.155");

        Page<FaultResult> page = new PageImpl<>(List.of(FAULT_RESULT_1, FAULT_RESULT_2), Pageable.unpaged(), 2);
        doReturn(page).when(shortCircuitService).getFaultResultsPage(NETWORK_UUID, VARIANT_ID, RESULT_UUID, FaultResultsMode.FULL, null, null, Pageable.unpaged());
        resultTest(RESULT_UUID, false, List.of(expectedHeaders, expectedLine1, expectedLine2));
    }

    void resultTest(UUID resultUuid, boolean oneBusCase, List<List<String>> expectedLines) throws Exception {
        MvcResult result;

        for (String language : List.of("fr", "en")) {
            result = mockMvc.perform(post(
                            "/" + VERSION + "/results/{resultUuid}/csv", resultUuid)
                            .param("networkUuid", NETWORK_UUID.toString())
                            .param("variantId", VARIANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(CsvExportParams.builder()
                                    .csvHeader(oneBusCase ? CSV_HEADERS_ONE_BUS : CSV_HEADERS_ALL_BUSES)
                                    .enumValueTranslations(ENUM_TRANSLATIONS)
                                    .language(language)
                                    .oneBusCase(oneBusCase).build())))
                    .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();
            byte[] zipFile = result.getResponse().getContentAsByteArray();
            byte[] unzippedCsvFile = unzip(zipFile);
            String unzippedCsvFileAsString = new String(unzippedCsvFile, StandardCharsets.UTF_8);
            List<List<String>> actualCsv = Arrays.stream(unzippedCsvFileAsString.split("\n"))
                    .map(line -> line.split(language.equals("fr") ? ";" : ",")) // csv separator
                    .map(fields -> Arrays.stream(fields)
                            .map(field -> language.equals("fr") ? field.replace(",", ".") : field) // number formating
                            .toList())
                    .toList();

            assertEquals(expectedLines, actualCsv);
        }
    }
}
