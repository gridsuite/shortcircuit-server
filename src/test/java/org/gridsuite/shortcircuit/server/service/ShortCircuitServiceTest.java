/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.Terminal.BusView;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.Fault;
import com.powsybl.shortcircuit.FaultParameters;
import com.powsybl.shortcircuit.ShortCircuitAnalysis;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.TestUtils;
import org.gridsuite.shortcircuit.server.reports.AbstractReportMapper;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({ MockitoExtension.class })
@Slf4j
class ShortCircuitServiceTest implements WithAssertions {

    @Mock
    private NetworkStoreService networkStoreService;

    @Mock
    private ReportService reportService;

    @Mock
    private ShortCircuitExecutionService shortCircuitExecutionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ShortCircuitAnalysisResultRepository resultRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AbstractReportMapper reportMapper;

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    private ShortCircuitWorkerService workerService;

    @BeforeEach
    void init() {
        workerService = new ShortCircuitWorkerService(
                networkStoreService,
                reportService,
                shortCircuitExecutionService,
                notificationService,
                resultRepository,
                objectMapper,
                Collections.singletonList(reportMapper),
                new ShortCircuitObserver(ObservationRegistry.create(), new SimpleMeterRegistry())
        );
    }

    @Test
    void testLogsMappersIsCalled() throws Exception {
        final ShortCircuitAnalysisResult analysisResult = new ShortCircuitAnalysisResult(List.of());
        final ShortCircuitAnalysisProvider providerMock = spy(new ShortCircuitAnalysisProviderMock(analysisResult));
        final Message<String> message = new GenericMessage<>("test");
        final UUID networkUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        final UUID reportUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        final UUID resultUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        final String reporterId = "44444444-4444-4444-4444-444444444444";
        final ShortCircuitRunContext runContext = new ShortCircuitRunContext(networkUuid, null, null,
                new ShortCircuitParameters(), reportUuid, reporterId, "AllBusesShortCircuitAnalysis", null, null);
        final ShortCircuitResultContext resultContext = new ShortCircuitResultContext(resultUuid, runContext);
        final Network.BusView busViewMocked = Mockito.mock(Network.BusView.class);
        final Reporter reporter = new ReporterModel("test", "test");

        try (final MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = TestUtils.injectShortCircuitAnalysisProvider(providerMock);
             final MockedStatic<ShortCircuitResultContext> shortCircuitResultContextMockedStatic = mockStatic(ShortCircuitResultContext.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(any(), anyList(), any(), any(), anyList(), any()))
                    .thenAnswer(invocation -> CompletableFuture.completedFuture(analysisResult));
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(any(), anyList(), any(), any(), anyList(), any()))
                    .thenAnswer(invocation -> CompletableFuture.completedFuture(analysisResult));
            shortCircuitResultContextMockedStatic.when(() -> ShortCircuitResultContext.fromMessage(message, objectMapper)).thenReturn(resultContext);
            when(networkStoreService.getNetwork(eq(networkUuid), any(PreloadingStrategy.class))).thenReturn(network);
            when(network.getVariantManager()).thenReturn(variantManager);
            when(network.getBusView()).thenReturn(busViewMocked);
            when(busViewMocked.getBusStream()).thenAnswer(invocation -> Stream.empty());
            when(reportMapper.processReporter(any(Reporter.class))).thenReturn(reporter);
            workerService.consumeRun().accept(message);
            shortCircuitAnalysisMockedStatic.verify(ShortCircuitAnalysis::find, atLeastOnce());
            verify(reportMapper, times(1)).processReporter(any(ReporterModel.class));
            verify(reportService, times(1)).sendReport(reportUuid, reporter);
        }
    }

    @Test
    void testGetBusFaultFromOutOfVoltageBus() throws Exception {
        var analysisProvider = spy(new ShortCircuitAnalysisProviderMock(new ShortCircuitAnalysisResult(Collections.emptyList())));
        var message = new GenericMessage<>("test");
        var runContext = mock(ShortCircuitRunContext.class);
        var resultContext = new ShortCircuitResultContext(UUID.randomUUID(), runContext);
        var busbarSection = mock(BusbarSection.class);
        var terminal = mock(Terminal.class);
        var busView = mock(BusView.class);
        var busId = "bus1";

        when(runContext.getBusId()).thenReturn(busId);
        when(networkStoreService.getNetwork(any(), any())).thenReturn(network);
        doReturn(busbarSection).when(network).getIdentifiable(busId);
        when(network.getVariantManager()).thenReturn(variantManager);
        when(busbarSection.getTerminal()).thenReturn(terminal);
        when(terminal.getBusView()).thenReturn(busView);
        when(busView.getBus()).thenReturn(null);

        try (var shortCircuitAnalysisMockedStatic = TestUtils.injectShortCircuitAnalysisProvider(analysisProvider);
             var shortCircuitResultContextMockedStatic = mockStatic(ShortCircuitResultContext.class)) {
            shortCircuitResultContextMockedStatic.when(() -> ShortCircuitResultContext.fromMessage(message, objectMapper)).thenReturn(resultContext);
            workerService.consumeRun().accept(message);
            verify(notificationService).publishFail(any(), any(), eq("Selected bus is out of voltage"), any(), eq(busId));
        }
    }

    @AllArgsConstructor
    private static class ShortCircuitAnalysisProviderMock implements ShortCircuitAnalysisProvider {
        private final ShortCircuitAnalysisResult result;

        @Override
        public String getName() {
            return "ShortCircuitAnalysisMock";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public CompletableFuture<ShortCircuitAnalysisResult> run(Network network, List<Fault> faults, ShortCircuitParameters parameters, ComputationManager computationManager, List<FaultParameters> faultParameters) {
            return CompletableFuture.completedFuture(this.result);
        }

        @Override
        public CompletableFuture<ShortCircuitAnalysisResult> run(Network network, List<Fault> faults, ShortCircuitParameters parameters, ComputationManager computationManager, List<FaultParameters> faultParameters, Reporter reporter) {
            return CompletableFuture.completedFuture(this.result);
        }
    }
}
