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
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.TestUtils;
import org.gridsuite.shortcircuit.server.reports.AbstractReportMapper;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;

@ExtendWith({ MockitoExtension.class })
@Slf4j
class ShortCircuitServiceTest implements WithAssertions {
    @Test
    void testLogsMappersIsCalled() throws Exception {
        final AbstractReportMapper reportMapperMocked = Mockito.mock(AbstractReportMapper.class);
        final NetworkStoreService networkStoreServiceMocked = Mockito.mock(NetworkStoreService.class);
        final ReportService reportServiceMocked = Mockito.mock(ReportService.class);
        final NotificationService notificationServiceMocked = Mockito.mock(NotificationService.class);
        final ShortCircuitAnalysisResultRepository resultRepositoryMocked = Mockito.mock(ShortCircuitAnalysisResultRepository.class);
        final ObjectMapper objectMapperMocked = Mockito.mock(ObjectMapper.class);

        final ShortCircuitAnalysisResult analysisResult = new ShortCircuitAnalysisResult(List.of());
        final ShortCircuitAnalysisProvider providerMock = Mockito.spy(new ShortCircuitAnalysisProviderMock(analysisResult));
        final Message<String> message = new GenericMessage<>("test");
        final UUID networkUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        final UUID reportUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        final UUID resultUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        final String reporterId = "44444444-4444-4444-4444-444444444444";
        final ShortCircuitRunContext runContext = new ShortCircuitRunContext(networkUuid, null, null, new ShortCircuitParameters(), reportUuid, reporterId, null, null);
        final ShortCircuitResultContext resultContext = new ShortCircuitResultContext(resultUuid, runContext);
        final Network networkMocked = Mockito.mock(Network.class);
        final VariantManager variantManagerMocked = Mockito.mock(VariantManager.class);
        final Network.BusView busViewMocked = Mockito.mock(Network.BusView.class);
        final Reporter reporter = new ReporterModel("test", "test");

        try (final MockedStatic<ShortCircuitAnalysis> shortCircuitAnalysisMockedStatic = TestUtils.injectShortCircuitAnalysisProvider(providerMock);
             final MockedStatic<ShortCircuitResultContext> shortCircuitResultContextMockedStatic = Mockito.mockStatic(ShortCircuitResultContext.class)) {
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(any(), anyList(), any(), any(), anyList(), any()))
                    .thenAnswer(invocation -> CompletableFuture.completedFuture(analysisResult));
            shortCircuitAnalysisMockedStatic.when(() -> ShortCircuitAnalysis.runAsync(any(), anyList(), any(), any(), anyList(), any()))
                    .thenAnswer(invocation -> CompletableFuture.completedFuture(analysisResult));
            shortCircuitResultContextMockedStatic.when(() -> ShortCircuitResultContext.fromMessage(message, objectMapperMocked)).thenReturn(resultContext);
            Mockito.when(networkStoreServiceMocked.getNetwork(eq(networkUuid), any(PreloadingStrategy.class))).thenReturn(networkMocked);
            Mockito.when(networkMocked.getVariantManager()).thenReturn(variantManagerMocked);
            Mockito.when(networkMocked.getBusView()).thenReturn(busViewMocked);
            Mockito.when(busViewMocked.getBusStream()).thenAnswer(invocation -> Stream.empty());
            Mockito.when(reportMapperMocked.processReporter(any(Reporter.class))).thenReturn(reporter);
            final ShortCircuitWorkerService workerService = new ShortCircuitWorkerService(networkStoreServiceMocked, reportServiceMocked, notificationServiceMocked, resultRepositoryMocked, objectMapperMocked, List.of(reportMapperMocked));
            workerService.consumeRun().accept(message);
            shortCircuitAnalysisMockedStatic.verify(ShortCircuitAnalysis::find, atLeastOnce());
            Mockito.verify(reportMapperMocked, times(1)).processReporter(any(ReporterModel.class));
            Mockito.verify(reportServiceMocked, times(1)).sendReport(reportUuid, reporter);
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
