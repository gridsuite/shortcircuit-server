/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuitAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.*;
import lombok.SneakyThrows;
import org.gridsuite.shortcircuit.server.dto.CsvTranslation;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
import org.gridsuite.shortcircuit.server.service.ReportService;
import org.gridsuite.shortcircuit.server.service.UuidGeneratorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.shortcircuit.server.TestUtils.unzip;
import static org.gridsuite.shortcircuit.server.service.NotificationService.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {ShortCircuitApplication.class, TestChannelBinderConfiguration.class})})
public class ShortCircuitAnalysisControllerTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID NETWORK1_UUID = UUID.fromString("faa0f351-f664-4771-951b-fa3b565c4d37");
    private static final UUID NODE_BREAKER_NETWORK_UUID = UUID.fromString("060d9225-1b88-4e52-885f-0f6297f5fa35");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");

    private static final UUID RESULT_UUID_TO_STOP = UUID.fromString("1c5212a9-e694-46a9-9eb2-cee60fc34675");
    private static final UUID REPORT_UUID = UUID.fromString("762b7298-8c0f-11ed-a1eb-0242ac120002");
    private static final ShortCircuitAnalysisResult RESULT = new ShortCircuitAnalysisResult(List.of());

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";
    private static final String VARIANT_4_ID = "variant_4";
    private static final String NODE_BREAKER_NETWORK_VARIANT_ID = "node_breaker_network_variant_id";

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

    private static final int TIMEOUT = 1000;

    private static final class ShortCircuitAnalysisResultMock {

        static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("CONN_ID_1", 22.17);
        static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("CONN_ID_2", 18.57);
        static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("CONN_ID_3", 53.94);
        static final FeederResult FEEDER_RESULT_4 = new FortescueFeederResult("CONN_ID_4", new FortescueValue(45.328664779663086, -12.69657966563543, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        static final FeederResult FEEDER_RESULT_5 = new FortescueFeederResult("CONN_ID_5", new FortescueValue(52.568678656325887, -33.09862779008776, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        static final FeederResult FEEDER_RESULT_6 = new FortescueFeederResult("CONN_ID_6", new FortescueValue(18.170874567665456, -90.29865576554445, Double.NaN, Double.NaN, Double.NaN, Double.NaN));

        static final LimitViolation LIMIT_VIOLATION_1 = new LimitViolation("SUBJECT_1", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 25.63, 4f, 33.54);
        static final LimitViolation LIMIT_VIOLATION_2 = new LimitViolation("SUBJECT_2", LimitViolationType.LOW_SHORT_CIRCUIT_CURRENT, 12.17, 2f, 10.56);
        static final LimitViolation LIMIT_VIOLATION_3 = new LimitViolation("SUBJECT_3", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 45.12, 5f, 54.3);

        static final FaultResult FAULT_RESULT_1 = new MagnitudeFaultResult(new BusFault("VLHV1_0", "ELEMENT_ID_1"), 17.0,
                List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
                45.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_2 = new MagnitudeFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
                List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(),
                47.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_3 = new MagnitudeFaultResult(new BusFault("VLGEN_0", "ELEMENT_ID_3"), 19.0,
                List.of(), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
                49.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_4 = new FortescueFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
            List.of(FEEDER_RESULT_4, FEEDER_RESULT_5, FEEDER_RESULT_6), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
            new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), Collections.emptyList(), null, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_BASIC_1 = new MagnitudeFaultResult(new BusFault("VLHV1_0", "ELEMENT_ID_1"), 17.0,
            List.of(), List.of(),
            45.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_BASIC_2 = new MagnitudeFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
            List.of(), List.of(),
            47.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_BASIC_3 = new MagnitudeFaultResult(new BusFault("VLGEN_0", "ELEMENT_ID_3"), 19.0,
            List.of(), List.of(),
            49.3, FaultResult.Status.SUCCESS);

        static final ShortCircuitAnalysisResult RESULT_MAGNITUDE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_2, FAULT_RESULT_3));
        static final ShortCircuitAnalysisResult RESULT_FORTESCUE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_4));
        static final ShortCircuitAnalysisResult RESULT_SORTED_PAGE_0 = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_3));
        static final ShortCircuitAnalysisResult RESULT_SORTED_PAGE_1 = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_3));
        static final ShortCircuitAnalysisResult RESULT_WITH_LIMIT_VIOLATIONS = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_3));
        static final ShortCircuitAnalysisResult RESULT_BASIC = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_BASIC_1, FAULT_RESULT_BASIC_2, FAULT_RESULT_BASIC_3));
    }

    private static class ShortCircuitAnalysisProviderMock implements ShortCircuitAnalysisProvider {
        @Override
        public String getName() {
            return "ShortCircuitAnalysisMock";
        }

        @Override
        public String getVersion() {
            return "1";
        }
    }

    // Destinations
    private final String shortCircuitAnalysisResultDestination = "shortcircuitanalysis.result";
    private final String shortCircuitAnalysisRunDestination = "shortcircuitanalysis.run";
    private final String shortCircuitAnalysisCancelDestination = "shortcircuitanalysis.cancel";
    private final String shortCircuitAnalysisStoppedDestination = "shortcircuitanalysis.stopped";
    private final String shortCircuitAnalysisFailedDestination = "shortcircuitanalysis.failed";

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    @MockBean
    ShortCircuitAnalysis.Runner runner;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;

    private Network nodeBreakerNetwork;

    private static void assertResultsEquals(ShortCircuitAnalysisResult result, org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto) {
        assertEquals(result.getFaultResults().size(), resultDto.getFaults().size());
        List<FaultResult> orderedFaultResults = result.getFaultResults().stream().sorted(Comparator.comparing(fr -> fr.getFault().getId())).collect(Collectors.toList());
        List<org.gridsuite.shortcircuit.server.dto.FaultResult> orderedFaultResultsDto = resultDto.getFaults().stream().sorted(Comparator.comparing(fr -> fr.getFault().getId())).collect(Collectors.toList());
        assertFaultResultsEquals(orderedFaultResults, orderedFaultResultsDto);
    }

    private static void assertPagedFaultResultsEquals(ShortCircuitAnalysisResult result, List<org.gridsuite.shortcircuit.server.dto.FaultResult> faultResults) {
        assertEquals(result.getFaultResults().size(), faultResults.size());
        var orderedFaultResults = faultResults.stream().sorted(Comparator.comparing(fr -> fr.getFault().getId())).collect(Collectors.toList());
        // don't need to sort here it's done in the paged request
        assertFaultResultsEquals(result.getFaultResults(), orderedFaultResults);
    }

    private static void assertFaultResultsEquals(List<FaultResult> faultResults, List<org.gridsuite.shortcircuit.server.dto.FaultResult> faultResultsDto) {
        for (int i = 0; i < faultResultsDto.size(); i++) {
            assertEquals(faultResultsDto.get(i).getFault().getId(), faultResults.get(i).getFault().getId());
            assertEquals(faultResultsDto.get(i).getFault().getElementId(), faultResults.get(i).getFault().getElementId());
            assertEquals(faultResultsDto.get(i).getFault().getFaultType(), faultResults.get(i).getFault().getFaultType().name());
            assertEquals(faultResultsDto.get(i).getShortCircuitPower(), faultResults.get(i).getShortCircuitPower(), 0.1);

            if (faultResults.get(i) instanceof MagnitudeFaultResult) {
                assertEquals(faultResultsDto.get(i).getCurrent(), ((MagnitudeFaultResult) faultResults.get(i)).getCurrent(), 0.1);
            } else if (faultResults.get(i) instanceof FortescueFaultResult) {
                assertEquals(faultResultsDto.get(i).getPositiveMagnitude(), ((FortescueFaultResult) faultResults.get(i)).getCurrent().getPositiveMagnitude(), 0.1);
            }

            List<LimitViolation> orderedLimitViolations = faultResults.get(i).getLimitViolations().stream().sorted(Comparator.comparing(lv -> lv.getSubjectId())).collect(Collectors.toList());
            List<org.gridsuite.shortcircuit.server.dto.LimitViolation> orderedLimitViolationsDto = faultResultsDto.get(i).getLimitViolations().stream().sorted(Comparator.comparing(lv -> lv.getSubjectId())).collect(Collectors.toList());
            assertEquals(orderedLimitViolationsDto.size(), orderedLimitViolations.size());
            for (int j = 0; j < orderedLimitViolationsDto.size(); j++) {
                assertEquals(orderedLimitViolationsDto.get(j).getSubjectId(), orderedLimitViolations.get(j).getSubjectId());
                assertEquals(orderedLimitViolationsDto.get(j).getLimitName(), orderedLimitViolations.get(j).getLimitName());
                assertEquals(orderedLimitViolationsDto.get(j).getLimitType(), orderedLimitViolations.get(j).getLimitType().name());
                assertEquals(orderedLimitViolationsDto.get(j).getLimit(), orderedLimitViolations.get(j).getLimit(), 0.1);
                assertEquals(orderedLimitViolationsDto.get(j).getValue(), orderedLimitViolations.get(j).getValue(), 0.1);
            }
            List<FeederResult> orderedFeederResults = faultResults.get(i).getFeederResults().stream().sorted(Comparator.comparing(fr -> fr.getConnectableId())).collect(Collectors.toList());
            List<org.gridsuite.shortcircuit.server.dto.FeederResult> orderedFeederResultsDto = faultResultsDto.get(i).getFeederResults().stream().sorted(Comparator.comparing(fr -> fr.getConnectableId())).collect(Collectors.toList());
            assertEquals(orderedFeederResultsDto.size(), orderedFeederResults.size());
            for (int j = 0; j < orderedFeederResultsDto.size(); j++) {
                assertEquals(orderedFeederResultsDto.get(j).getConnectableId(), orderedFeederResults.get(j).getConnectableId());
                if (faultResults.get(i) instanceof MagnitudeFaultResult) {
                    assertEquals(orderedFeederResultsDto.get(j).getCurrent(), ((MagnitudeFeederResult) orderedFeederResults.get(j)).getCurrent(), 0.1);
                } else if (faultResults.get(i) instanceof FortescueFaultResult) {
                    assertEquals(orderedFeederResultsDto.get(j).getPositiveMagnitude(), ((FortescueFeederResult) orderedFeederResults.get(j)).getCurrent().getPositiveMagnitude(), 0.1);
                }
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVoltageLevels().forEach(voltageLevel -> voltageLevel.newExtension(IdentifiableShortCircuitAdder.class).withIpMin(10.5).withIpMax(200.0).add());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVoltageLevelStream().findFirst().get().newExtension(IdentifiableShortCircuitAdder.class).withIpMin(5.0).withIpMax(100.5).add();
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_4_ID);
        given(networkStoreService.getNetwork(NETWORK1_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network1);

        // Network for nodeBreakerView tests
        nodeBreakerNetwork = FourSubstationsNodeBreakerFactory.create(new NetworkFactoryImpl());
        nodeBreakerNetwork.getVoltageLevels().forEach(voltageLevel -> voltageLevel.newExtension(IdentifiableShortCircuitAdder.class).withIpMin(10.5).withIpMax(200.0).add());
        nodeBreakerNetwork.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, NODE_BREAKER_NETWORK_VARIANT_ID);

        given(networkStoreService.getNetwork(NODE_BREAKER_NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(nodeBreakerNetwork);

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);
    }

    @SneakyThrows
    @After
    public void tearDown() {
        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(List.of(shortCircuitAnalysisResultDestination, shortCircuitAnalysisRunDestination,
            shortCircuitAnalysisCancelDestination, shortCircuitAnalysisStoppedDestination,
            shortCircuitAnalysisFailedDestination), output);
    }

    @SneakyThrows
    private void cleanDB() {
        mockMvc.perform(delete("/" + VERSION + "/results")).andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {

            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_MAGNITUDE_FULL));
            shortCircuitAnalysisMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            when(runner.getName()).thenReturn("providerTest");

            ShortCircuitParameters shortCircuitParameters = new ShortCircuitParameters();
            shortCircuitParameters.setWithFortescueResult(false);
            String parametersJson = mapper.writeValueAsString(shortCircuitParameters);
            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=AllBusesShortCircuitAnalysis&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(parametersJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));

            // WITH_LIMIT_VIOLATIONS mode (default)
            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_WITH_LIMIT_VIOLATIONS, resultDto);

            // BASIC mode
            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID)
                    .param("mode", "BASIC"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDtoBasic = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_BASIC, resultDtoBasic);

            // FULL mode
            result = mockMvc.perform(get(
                             "/" + VERSION + "/results/{resultUuid}", RESULT_UUID)
                            .param("mode", "FULL"))
                     .andExpect(status().isOk())
                     .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                     .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDtoFull = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_MAGNITUDE_FULL, resultDtoFull);

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}/fault_results/paged", RESULT_UUID)
                            .param("mode", "WITH_LIMIT_VIOLATIONS")
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            // It's not easy to deserialize into a Page implementation
            // ( e.g. https://stackoverflow.com/questions/52490399/spring-boot-page-deserialization-pageimpl-no-constructor ),
            // but for tests we care only about the content so we deserialize to DTOs only the content subfield using the jackson treemodel api
            JsonNode faultResultsPageNode = mapper.readTree(result.getResponse().getContentAsString());
            ObjectReader faultResultsReader = mapper.readerFor(new TypeReference<List<org.gridsuite.shortcircuit.server.dto.FaultResult>>() { });
            List<org.gridsuite.shortcircuit.server.dto.FaultResult> faultResultsPageDto0 = faultResultsReader.readValue(faultResultsPageNode.get("content"));
            assertPagedFaultResultsEquals(ShortCircuitAnalysisResultMock.RESULT_WITH_LIMIT_VIOLATIONS, faultResultsPageDto0);

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}/fault_results/paged", RESULT_UUID)
                             .param("mode", "FULL")
                             .param("page", "0")
                             .param("size", "2")
                             .param("sort", "fault.id"))
                     .andExpect(status().isOk())
                     .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                     .andReturn();
            JsonNode faultResultsPageNode0 = mapper.readTree(result.getResponse().getContentAsString());
            List<org.gridsuite.shortcircuit.server.dto.FaultResult> faultResultsPageDto0Full = faultResultsReader.readValue(faultResultsPageNode0.get("content"));
            assertPagedFaultResultsEquals(ShortCircuitAnalysisResultMock.RESULT_SORTED_PAGE_0, faultResultsPageDto0Full);

            result = mockMvc.perform(get(
                             "/" + VERSION + "/results/{resultUuid}/fault_results/paged", RESULT_UUID)
                             .param("mode", "FULL")
                             .param("page", "1")
                             .param("size", "2")
                             .param("sort", "fault.id,DESC"))
                     .andExpect(status().isOk())
                     .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                     .andReturn();
            JsonNode faultResultsPageNode1 = mapper.readTree(result.getResponse().getContentAsString());
            List<org.gridsuite.shortcircuit.server.dto.FaultResult> faultResultsPageDto1Full = faultResultsReader.readValue(faultResultsPageNode1.get("content"));
            assertPagedFaultResultsEquals(ShortCircuitAnalysisResultMock.RESULT_SORTED_PAGE_1, faultResultsPageDto1Full);

            // export zipped csv result
            result = mockMvc.perform(post(
                            "/" + VERSION + "/results/{resultUuid}/csv", RESULT_UUID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(CsvTranslation.builder().headersCsv(CSV_HEADERS).
                                    enumValueTranslations(enumTranslations).build())))
                    .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();
            byte[] zipFile = result.getResponse().getContentAsByteArray();
            byte[] unzippedCsvFile = unzip(zipFile);
            String unzippedCsvFileAsString = new String(unzippedCsvFile, StandardCharsets.UTF_8);
            List<String> actualCsvLines = List.of(Arrays.asList(unzippedCsvFileAsString.split("\n")).get(0).split(","));
            List<String> expectedLines = new ArrayList<>(CSV_HEADERS);
            assertEquals(expectedLines, actualCsvLines);

            // should throw not found if result does not exist
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
                    .andExpect(status().isNotFound());

            // test one result deletion
            mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    public void runWithBusIdTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FORTESCUE_FULL));
            shortCircuitAnalysisMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            when(runner.getName()).thenReturn("providerTest");

            ShortCircuitParameters shortCircuitParameters = new ShortCircuitParameters();
            shortCircuitParameters.setWithFortescueResult(true);
            String parametersJson = mapper.writeValueAsString(shortCircuitParameters);
            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=OneBusShortCircuitAnalysis&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                    .param(HEADER_BUS_ID, "NGEN")
                    .header(HEADER_USER_ID, "userId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(parametersJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals("NGEN", resultMessage.getHeaders().get(HEADER_BUS_ID));

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));
            assertEquals("NGEN", runMessage.getHeaders().get(HEADER_BUS_ID));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_FORTESCUE_FULL, resultDto);

            // paged results (more deeply tested in unit tests)
            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}/feeder_results/paged", RESULT_UUID)
                            .param("page", "0")
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            JsonNode feederResultsPage = mapper.readTree(result.getResponse().getContentAsString());
            org.gridsuite.shortcircuit.server.dto.FaultResult faultResult = resultDto.getFaults().get(0);
            ObjectReader reader = mapper.readerFor(new TypeReference<List<org.gridsuite.shortcircuit.server.dto.FeederResult>>() { });
            List<org.gridsuite.shortcircuit.server.dto.FeederResult> feederResults = reader.readValue(feederResultsPage.get("content"));
            // we update the fault result with the feeders we just get to be able to use the assertion
            org.gridsuite.shortcircuit.server.dto.FaultResult formattedFaultResult = new org.gridsuite.shortcircuit.server.dto.FaultResult(faultResult.getFault(), faultResult.getCurrent(), faultResult.getPositiveMagnitude(), faultResult.getShortCircuitPower(), faultResult.getLimitViolations(), feederResults, faultResult.getShortCircuitLimits());
            assertPagedFaultResultsEquals(ShortCircuitAnalysisResultMock.RESULT_FORTESCUE_FULL, List.of(formattedFaultResult));
        }
    }

    @Test
    public void runWithBusBarSectionIdTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(nodeBreakerNetwork), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FORTESCUE_FULL));
            shortCircuitAnalysisMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            when(runner.getName()).thenReturn("providerTest");

            ShortCircuitParameters shortCircuitParameters = new ShortCircuitParameters();
            shortCircuitParameters.setWithFortescueResult(true);
            String parametersJson = mapper.writeValueAsString(shortCircuitParameters);
            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=OneBusShortCircuitAnalysis&receiver=me&variantId=" + NODE_BREAKER_NETWORK_VARIANT_ID, NODE_BREAKER_NETWORK_UUID)
                    .param(HEADER_BUS_ID, "S1VL2_BBS1")
                    .header(HEADER_USER_ID, "userId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(parametersJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals("S1VL2_BBS1", resultMessage.getHeaders().get(HEADER_BUS_ID));

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));
            assertEquals("S1VL2_BBS1", runMessage.getHeaders().get(HEADER_BUS_ID));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_FORTESCUE_FULL, resultDto);
        }
    }

    @Test
    public void runWithBusBarSectionIdAndErrorTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(nodeBreakerNetwork), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_MAGNITUDE_FULL));

            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=OneBusShortCircuitAnalysis&receiver=me&variantId=" + NODE_BREAKER_NETWORK_VARIANT_ID, NODE_BREAKER_NETWORK_UUID)
                    .param(HEADER_BUS_ID, "BUSBARSECTION_ID_NOT_EXISTING")
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));
            assertEquals("BUSBARSECTION_ID_NOT_EXISTING", runMessage.getHeaders().get(HEADER_BUS_ID));

            Message<byte[]> failedMessage = output.receive(TIMEOUT, shortCircuitAnalysisFailedDestination);
            assertEquals(RESULT_UUID.toString(), failedMessage.getHeaders().get("resultUuid"));
            assertEquals("me", failedMessage.getHeaders().get("receiver"));
            assertEquals("BUSBARSECTION_ID_NOT_EXISTING", failedMessage.getHeaders().get(HEADER_BUS_ID));

            mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    public void stopTest() throws Exception {
        // this test breaks following tests executing shortcircuit analysis if they run with the same resultUUID by adding it to cancelComputationRequests
        // for now, we need to run it with a different resultUuid
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID_TO_STOP);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESULT));
            shortCircuitAnalysisMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            when(runner.getName()).thenReturn("providerTest");

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=AllBusesShortCircuitAnalysis&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            // stop shortcircuit analysis
            assertNotNull(output.receive(TIMEOUT, shortCircuitAnalysisRunDestination));
            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID_TO_STOP))
                    .andExpect(status().isOk());

            assertNotNull(output.receive(TIMEOUT, shortCircuitAnalysisResultDestination));
            assertNotNull(output.receive(TIMEOUT, shortCircuitAnalysisCancelDestination));

            Message<byte[]> message = output.receive(TIMEOUT, shortCircuitAnalysisStoppedDestination);
            assertNotNull(message);
            assertEquals(RESULT_UUID_TO_STOP.toString(), message.getHeaders().get("resultUuid"));
            assertEquals("me", message.getHeaders().get("receiver"));
            assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));
        }
    }

    @SneakyThrows
    @Test
    public void testStatus() {
        MvcResult result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("", result.getResponse().getContentAsString());

        mockMvc.perform(put("/" + VERSION + "/results/invalidate-status?resultUuid=" + RESULT_UUID))
                .andExpect(status().isOk());

        result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(ShortCircuitAnalysisStatus.NOT_DONE.name(), result.getResponse().getContentAsString());
    }

    @SneakyThrows
    @Test
    public void runWithReportTest() {
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = TestUtils.injectShortCircuitAnalysisProvider(new ShortCircuitAnalysisProviderMock())) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportType=AllBusesShortCircuitAnalysis&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "user"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
        }
    }

    @SneakyThrows
    @Test
    public void runWithNoShortcircuitDataTest() {
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = TestUtils.injectShortCircuitAnalysisProvider(new ShortCircuitAnalysisProviderMock())) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network1), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(RESULT));

            mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportType=AllBusesShortCircuitAnalysis&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                    .header(HEADER_USER_ID, "user"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            Message<byte[]> runMessage = output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);
            assertEquals(RESULT_UUID.toString(), runMessage.getHeaders().get("resultUuid"));
            assertEquals("me", runMessage.getHeaders().get("receiver"));

            //network1 has no shortcircuit data so the computation should fail
            Message<byte[]> resultMessage = output.receive(TIMEOUT, shortCircuitAnalysisFailedDestination);
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
        }
    }

    @Test
    public void checkShortCircuitLimitsTest() throws Exception {
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_MAGNITUDE_FULL));
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network1), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_MAGNITUDE_FULL));
            shortCircuitAnalysisMockedStatic.when(ShortCircuitAnalysis::find).thenReturn(runner);
            when(runner.getName()).thenReturn("providerTest");

            ShortCircuitParameters shortCircuitParameters = new ShortCircuitParameters().setWithFortescueResult(false);
            String parametersJson = mapper.writeValueAsString(shortCircuitParameters);
            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(parametersJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);

            MvcResult result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertEquals(10.5, resultDto.getFaults().get(0).getShortCircuitLimits().getIpMin(), 0.1);
            assertEquals(200.0, resultDto.getFaults().get(0).getShortCircuitLimits().getIpMax(), 0.1);
            assertEquals(10.5, resultDto.getFaults().get(1).getShortCircuitLimits().getIpMin(), 0.1);
            assertEquals(200.0, resultDto.getFaults().get(1).getShortCircuitLimits().getIpMax(), 0.1);
            mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk());

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_4_ID, NETWORK1_UUID)
                            .header(HEADER_USER_ID, "userId")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(parametersJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            output.receive(TIMEOUT, shortCircuitAnalysisResultDestination);
            output.receive(TIMEOUT, shortCircuitAnalysisRunDestination);

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertEquals(Double.NaN, resultDto.getFaults().get(0).getShortCircuitLimits().getIpMin(), 0.1);
            assertEquals(Double.NaN, resultDto.getFaults().get(0).getShortCircuitLimits().getIpMax(), 0.1);
            assertEquals(5.0, resultDto.getFaults().get(1).getShortCircuitLimits().getIpMin(), 0.1);
            assertEquals(100.5, resultDto.getFaults().get(1).getShortCircuitLimits().getIpMax(), 0.1);
        }
    }
}
