/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.shortcircuit.FaultResult;
import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.gridsuite.shortcircuit.server.service.ReportService;
import org.gridsuite.shortcircuit.server.service.ShortCircuitWorkerService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {ShortCircuitApplication.class, TestChannelBinderConfiguration.class})})
public class ShortCircuitAnalysisControllerTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID NETWORK_STOP_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID REPORT_UUID = UUID.fromString("0c4de370-3e6a-4d72-b292-d355a97e0d53");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");
    private static final ShortCircuitAnalysisResult RESULT = new ShortCircuitAnalysisResult(List.of());

    private static final ShortCircuitAnalysisResult RESULT_VARIANT = new ShortCircuitAnalysisResult(List.of());

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final int TIMEOUT = 1000;

    private static final String ERROR_MESSAGE = "Error message test";

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ReportService reportService;

    @SpyBean
    private ShortCircuitWorkerService workerService;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.COLLECTION)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.COLLECTION)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        when(networkStoreService.getNetwork(NETWORK_STOP_UUID, PreloadingStrategy.COLLECTION)).thenAnswer((Answer<?>) invocation -> {
            //Needed so the stop call doesn't arrive too late
            Thread.sleep(3000);
            return network1;
        });

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // mock the powsybl sensitivity analysis runner
        ShortCircuitAnalysis.Runner runner = mock(ShortCircuitAnalysis.Runner.class);
//        given(runner.runAsync(eq(network), eq(VariantManagerConstants.INITIAL_VARIANT_ID), anyList(), anyList(), anyList(), any(ShortCircuitParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
//        given(runner.runAsync(eq(network), eq(VARIANT_1_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
//        given(runner.runAsync(eq(network), eq(VARIANT_2_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
//        given(runner.runAsync(eq(network), eq(VARIANT_3_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT_VARIANT));
//        given(runner.runAsync(eq(network1), eq(VARIANT_2_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
//        workerService.setSensitivityAnalysisFactorySupplier(provider -> runner);

        // purge messages
        while (output.receive(1000, "sensitivityanalysis.result") != null) {
        }
        // purge messages
        while (output.receive(1000, "sensitivityanalysis.run") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.cancel") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.stopped") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.failed") != null) {
        }
    }

    @Test
    public void runTest() {
        assertEquals(1, 1);
    }
}
