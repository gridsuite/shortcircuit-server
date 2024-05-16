/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import org.gridsuite.shortcircuit.server.computation.service.*;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.ShortCircuitException;
import org.gridsuite.shortcircuit.server.reports.AbstractReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.shortcircuit.server.ShortCircuitException.Type.BUS_OUT_OF_VOLTAGE;
import static org.gridsuite.shortcircuit.server.computation.service.NotificationService.getFailedMessage;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitWorkerService extends AbstractWorkerService<ShortCircuitAnalysisResult, ShortCircuitRunContext, ShortCircuitParameters, ShortCircuitAnalysisResultService> {

    public static final String COMPUTATION_TYPE = "Short circuit analysis";
    public static final String HEADER_BUS_ID = "busId";

    @Autowired
    public ShortCircuitWorkerService(NetworkStoreService networkStoreService, ReportService reportService, ExecutionService executionService,
                                     NotificationService notificationService, ShortCircuitAnalysisResultService resultService,
                                     ObjectMapper objectMapper, Collection<AbstractReportMapper> reportMappers, ShortCircuitObserver shortCircuitObserver) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, shortCircuitObserver, objectMapper, reportMappers);
    }

    @Override
    protected PreloadingStrategy getNetworkPreloadingStrategy() {
        return PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW;
    }

    @Override
    protected ShortCircuitResultContext fromMessage(Message<String> message) {
        return ShortCircuitResultContext.fromMessage(message, objectMapper);
    }

    @Override
    protected void saveResult(Network network, AbstractResultContext<ShortCircuitRunContext> resultContext, ShortCircuitAnalysisResult result) {
        resultService.insert(resultContext.getResultUuid(),
                result,
                resultContext.getRunContext(),
                ShortCircuitAnalysisStatus.COMPLETED.name());
    }

    @Override
    protected void sendResultMessage(AbstractResultContext<ShortCircuitRunContext> resultContext, ShortCircuitAnalysisResult result) {
        ShortCircuitRunContext context = resultContext.getRunContext();
        String busId = context.getBusId();
        Map<String, Object> additionalHeaders = new HashMap<>();
        additionalHeaders.put(HEADER_BUS_ID, busId);
        notificationService.sendResultMessage(resultContext.getResultUuid(), context.getReceiver(), additionalHeaders);
    }

    @Override
    protected void publishFail(AbstractResultContext<ShortCircuitRunContext> resultContext, String message) {
        ShortCircuitRunContext context = resultContext.getRunContext();
        String busId = context.getBusId();
        Map<String, Object> additionalHeaders = new HashMap<>();
        additionalHeaders.put(HEADER_BUS_ID, busId);
        notificationService.publishFail(resultContext.getResultUuid(), context.getReceiver(),
                message, context.getUserId(), getFailedMessage(getComputationType()), additionalHeaders);
    }

    @Override
    protected CompletableFuture<ShortCircuitAnalysisResult> getCompletableFuture(Network network, ShortCircuitRunContext runContext, String provider, UUID resultUuid) {
        List<Fault> faults = runContext.getBusId() == null ? getAllBusfaultFromNetwork(network, runContext) : getBusFaultFromBusId(network, runContext);
        CompletableFuture<ShortCircuitAnalysisResult> future = ShortCircuitAnalysis.runAsync(network,
                faults,
                runContext.getParameters(),
                executionService.getComputationManager(),
                List.of(),
                runContext.getReporter());
        if (resultUuid != null) {
            futures.put(resultUuid, future);
        }
        return future;
    }

    private List<Fault> getAllBusfaultFromNetwork(Network network, ShortCircuitRunContext context) {
        Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
        List<Fault> faults = network.getBusView().getBusStream()
                .map(bus -> {
                    IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = bus.getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
                    if (shortCircuitExtension != null) {
                        shortCircuitLimits.put(bus.getId(), new ShortCircuitLimits(shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
                    }
                    return new BusFault(bus.getId(), bus.getId());
                })
                .collect(Collectors.toList());
        context.setShortCircuitLimits(shortCircuitLimits);
        return faults;
    }

    private List<Fault> getBusFaultFromBusId(Network network, ShortCircuitRunContext context) {
        String busId = context.getBusId();
        Identifiable<?> identifiable = network.getIdentifiable(busId);
        Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();

        if (identifiable instanceof BusbarSection busbarSection) {
            Bus bus = busbarSection.getTerminal().getBusView().getBus();
            if (bus == null) {
                throw new ShortCircuitException(BUS_OUT_OF_VOLTAGE, "Selected bus is out of voltage");
            }
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = ((BusbarSection) identifiable).getTerminal().getBusView().getBus().getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null) {
                shortCircuitLimits.put(bus.getId(), new ShortCircuitLimits(shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
            }
            context.setShortCircuitLimits(shortCircuitLimits);
            return List.of(new BusFault(bus.getId(), bus.getId()));
        }

        if (identifiable instanceof Bus) {
            String busIdFromBusView = ((Bus) identifiable).getVoltageLevel().getBusView().getMergedBus(busId).getId();
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = ((Bus) identifiable).getVoltageLevel().getBusView().getMergedBus(busId).getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null) {
                shortCircuitLimits.put(busIdFromBusView, new ShortCircuitLimits(shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
            }
            context.setShortCircuitLimits(shortCircuitLimits);
            return List.of(new BusFault(busIdFromBusView, busIdFromBusView));
        }
        throw new NoSuchElementException("No bus found for bus id " + busId);
    }

    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeRun() {
        return super.consumeRun();
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeCancel() {
        return super.consumeCancel();
    }
}
