/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.*;
import lombok.SneakyThrows;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.shortcircuit.server.service.NotificationService.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
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
    private static final UUID NODE_BREAKER_NETWORK_UUID = UUID.fromString("060d9225-1b88-4e52-885f-0f6297f5fa35");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");
    private static final UUID REPORT_UUID = UUID.fromString("762b7298-8c0f-11ed-a1eb-0242ac120002");
    private static final ShortCircuitAnalysisResult RESULT = new ShortCircuitAnalysisResult(List.of());

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final String NODE_BREAKER_NETWORK_VARIANT_ID = "node_breaker_network_variant_id";

    private static final int TIMEOUT = 1000;

    private static final class ShortCircuitAnalysisResultMock {

        static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("CONN_ID_1", 22.17);
        static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("CONN_ID_2", 18.57);
        static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("CONN_ID_3", 53.94);

        static final LimitViolation LIMIT_VIOLATION_1 = new LimitViolation("SUBJECT_1", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 25.63, 4f, 33.54);
        static final LimitViolation LIMIT_VIOLATION_2 = new LimitViolation("SUBJECT_2", LimitViolationType.LOW_SHORT_CIRCUIT_CURRENT, 12.17, 2f, 10.56);
        static final LimitViolation LIMIT_VIOLATION_3 = new LimitViolation("SUBJECT_3", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 45.12, 5f, 54.3);

        static final FaultResult FAULT_RESULT_1 = new MagnitudeFaultResult(new BusFault("FAULT_1", "ELEMENT_ID_1"), 17.0,
                List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
                45.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_2 = new MagnitudeFaultResult(new BusFault("FAULT_2", "ELEMENT_ID_2"), 18.0,
                List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(),
                47.3, FaultResult.Status.SUCCESS);
        static final FaultResult FAULT_RESULT_3 = new MagnitudeFaultResult(new BusFault("FAULT_3", "ELEMENT_ID_3"), 19.0,
                List.of(), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
                49.3, FaultResult.Status.SUCCESS);

        static final ShortCircuitAnalysisResult RESULT_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_2, FAULT_RESULT_3));
        static final ShortCircuitAnalysisResult RESULT = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_3));
    }

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

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;

    private Network nodeBreakerNetwork;

    private static void assertResultsEquals(ShortCircuitAnalysisResult result, org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto) {
        assertEquals(result.getFaultResults().size(), resultDto.getFaults().size());
        List<FaultResult> orderedFaultResults = result.getFaultResults().stream().sorted(Comparator.comparing(fr -> fr.getFault().getId())).collect(Collectors.toList());
        List<org.gridsuite.shortcircuit.server.dto.FaultResult> orderedFaultResultsDto = resultDto.getFaults().stream().sorted(Comparator.comparing(fr -> fr.getFault().getId())).collect(Collectors.toList());
        for (int i = 0; i < orderedFaultResultsDto.size(); i++) {
            assertEquals(orderedFaultResultsDto.get(i).getFault().getId(), orderedFaultResults.get(i).getFault().getId());
            assertEquals(orderedFaultResultsDto.get(i).getFault().getElementId(), orderedFaultResults.get(i).getFault().getElementId());
            assertEquals(orderedFaultResultsDto.get(i).getFault().getFaultType(), orderedFaultResults.get(i).getFault().getFaultType().name());
            assertEquals(orderedFaultResultsDto.get(i).getShortCircuitPower(), orderedFaultResults.get(i).getShortCircuitPower(), 0.1);
            assertEquals(orderedFaultResultsDto.get(i).getCurrent(), ((MagnitudeFaultResult) orderedFaultResults.get(i)).getCurrent(), 0.1);
            List<LimitViolation> orderedLimitViolations = orderedFaultResults.get(i).getLimitViolations().stream().sorted(Comparator.comparing(lv -> lv.getSubjectId())).collect(Collectors.toList());
            List<org.gridsuite.shortcircuit.server.dto.LimitViolation> orderedLimitViolationsDto = orderedFaultResultsDto.get(i).getLimitViolations().stream().sorted(Comparator.comparing(lv -> lv.getSubjectId())).collect(Collectors.toList());
            assertEquals(orderedLimitViolationsDto.size(), orderedLimitViolations.size());
            for (int j = 0; j < orderedLimitViolationsDto.size(); j++) {
                assertEquals(orderedLimitViolationsDto.get(j).getSubjectId(), orderedLimitViolations.get(j).getSubjectId());
                assertEquals(orderedLimitViolationsDto.get(j).getLimitName(), orderedLimitViolations.get(j).getLimitName());
                assertEquals(orderedLimitViolationsDto.get(j).getLimitType(), orderedLimitViolations.get(j).getLimitType().name());
                assertEquals(orderedLimitViolationsDto.get(j).getLimit(), orderedLimitViolations.get(j).getLimit(), 0.1);
                assertEquals(orderedLimitViolationsDto.get(j).getValue(), orderedLimitViolations.get(j).getValue(), 0.1);
            }
            List<FeederResult> orderedFeederResults = orderedFaultResults.get(i).getFeederResults().stream().sorted(Comparator.comparing(fr -> fr.getConnectableId())).collect(Collectors.toList());
            List<org.gridsuite.shortcircuit.server.dto.FeederResult> orderedFeederResultsDto = orderedFaultResultsDto.get(i).getFeederResults().stream().sorted(Comparator.comparing(fr -> fr.getConnectableId())).collect(Collectors.toList());
            assertEquals(orderedFeederResultsDto.size(), orderedFeederResults.size());
            for (int j = 0; j < orderedFeederResultsDto.size(); j++) {
                assertEquals(orderedFeederResultsDto.get(j).getConnectableId(), orderedFeederResults.get(j).getConnectableId());
                assertEquals(orderedFeederResultsDto.get(j).getCurrent(), ((MagnitudeFeederResult) orderedFeederResults.get(j)).getCurrent(), 0.1);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);

        // Network for nodeBreakerView tests
        nodeBreakerNetwork = FourSubstationsNodeBreakerFactory.create(new NetworkFactoryImpl());
        nodeBreakerNetwork.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, NODE_BREAKER_NETWORK_VARIANT_ID);

        given(networkStoreService.getNetwork(NODE_BREAKER_NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(nodeBreakerNetwork);

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // purge messages
        while (output.receive(100, "shortcircuitanalysis.result") != null) {
        }
        // purge messages
        while (output.receive(100, "shortcircuitanalysis.run") != null) {
        }
        while (output.receive(100, "shortcircuitanalysis.cancel") != null) {
        }
        while (output.receive(100, "shortcircuitanalysis.stopped") != null) {
        }
        while (output.receive(100, "shortcircuitanalysis.failed") != null) {
        }
    }

    @SneakyThrows
    @After
    public void tearDown() {
        mockMvc.perform(delete("/" + VERSION + "/results"))
                .andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FULL));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "shortcircuitanalysis.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT, resultDto);

            result = mockMvc.perform(get(
                             "/" + VERSION + "/results/{resultUuid}?full=true", RESULT_UUID))
                     .andExpect(status().isOk())
                     .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                     .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDtoFull = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT_FULL, resultDtoFull);

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
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FULL));

            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                    .param(HEADER_BUS_ID, "NGEN")
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "shortcircuitanalysis.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals("NGEN", resultMessage.getHeaders().get(HEADER_BUS_ID));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT, resultDto);
        }
    }

    @Test
    public void runWithBusBarSectionIdTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(nodeBreakerNetwork), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FULL));

            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + NODE_BREAKER_NETWORK_VARIANT_ID, NODE_BREAKER_NETWORK_UUID)
                    .param(HEADER_BUS_ID, "S1VL2_BBS1")
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "shortcircuitanalysis.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals("S1VL2_BBS1", resultMessage.getHeaders().get(HEADER_BUS_ID));

            result = mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisResult.class);
            assertResultsEquals(ShortCircuitAnalysisResultMock.RESULT, resultDto);
        }
    }

    @Test
    public void runWithBusBarSectionIdAndErrorTest() throws Exception {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime testTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(nodeBreakerNetwork), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                .thenReturn(CompletableFuture.completedFuture(ShortCircuitAnalysisResultMock.RESULT_FULL));

            MvcResult result = mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + NODE_BREAKER_NETWORK_VARIANT_ID, NODE_BREAKER_NETWORK_UUID)
                    .param(HEADER_BUS_ID, "BUSBARSECTION_ID_NOT_EXISTING")
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(TIMEOUT, "shortcircuitanalysis.failed");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));
            assertEquals("BUSBARSECTION_ID_NOT_EXISTING", resultMessage.getHeaders().get(HEADER_BUS_ID));

            mockMvc.perform(get(
                    "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    public void stopTest() throws Exception {
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESULT));

            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
                    .andExpect(status().isOk());
            assertNotNull(output.receive(TIMEOUT, "shortcircuitanalysis.cancel"));

            Message<byte[]> message = output.receive(TIMEOUT, "shortcircuitanalysis.stopped");
            assertNotNull(message);
            assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
            assertEquals("me", message.getHeaders().get("receiver"));
            assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));

            // short circuit analysis is run here, otherwise cancelComputationRequests is filled with RESULT_UUID, which can break other tests
            // here, the below run will be canceled then remove RESULT_UUID from cancelComputationRequests
            mockMvc.perform(post(
                    "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                    .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

            // stop shortcircuit analysis
            assertNotNull(output.receive(TIMEOUT, "shortcircuitanalysis.run"));
        }
    }

    @SneakyThrows
    @Test
    public void mergingViewTest() {
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&networkUuid=" + NETWORK_FOR_MERGING_VIEW_UUID, OTHER_NETWORK_FOR_MERGING_VIEW_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));
        }
    }

    //FIXME: test to be removed when the hack in ShortCircuitParameters.getNonNullParameters() is removed
    @SneakyThrows
    @Test
    public void parametersWithExtentionTest() {

        class ShortCircuitParametersRandomExtension extends AbstractExtension<ShortCircuitParameters> {
            @Override
            public String getName() {
                return "RandomExtension";
            }
        }

        ShortCircuitParameters parametersWithExtentions = new ShortCircuitParameters();
        parametersWithExtentions.addExtension(ShortCircuitParametersRandomExtension.class, new ShortCircuitParametersRandomExtension());

        try (MockedStatic<ShortCircuitParameters> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitParameters.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitParameters.load())
                    .thenReturn(parametersWithExtentions);

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));
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
        try (MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = Mockito.mockStatic(ShortCircuitAnalysis.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(eq(network), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), anyList(), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "user"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
        }
    }
}
