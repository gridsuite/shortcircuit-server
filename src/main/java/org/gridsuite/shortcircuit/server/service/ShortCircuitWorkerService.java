/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.service.*;
import org.gridsuite.shortcircuit.server.ShortCircuitException;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.report.ReportMapperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.shortcircuit.server.ShortCircuitException.Type.*;
import static org.gridsuite.shortcircuit.server.service.ShortCircuitResultContext.HEADER_BUS_ID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitWorkerService extends AbstractWorkerService<ShortCircuitAnalysisResult, ShortCircuitRunContext, ShortCircuitParameters, ShortCircuitAnalysisResultService> {
    public static final String COMPUTATION_TYPE = "Short circuit analysis";
    private final ReportMapperService reportMapper;

    public ShortCircuitWorkerService(NetworkStoreService networkStoreService,
                                     ReportService reportService,
                                     ExecutionService executionService,
                                     NotificationService notificationService,
                                     ShortCircuitAnalysisResultService resultService,
                                     @Autowired(required = false)
                                     ComputationS3Service computationS3Service,
                                     ObjectMapper objectMapper,
                                     ReportMapperService reportMapper,
                                     ShortCircuitObserver shortCircuitObserver) {
        super(networkStoreService, notificationService, reportService, resultService, computationS3Service, executionService, shortCircuitObserver, objectMapper);
        this.reportMapper = reportMapper;
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
    public void preRun(ShortCircuitRunContext resultContext) {
        checkInconsistentVoltageLevels(resultContext);
        super.preRun(resultContext);
    }

    private void checkInconsistentVoltageLevels(ShortCircuitRunContext resultContext) {
        List<String> inconsistentVoltageLevels = new ArrayList<>();
        resultContext.getNetwork().getVoltageLevelStream().forEach(vl -> {
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = vl.getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null && shortCircuitExtension.getIpMin() > shortCircuitExtension.getIpMax()) {
                inconsistentVoltageLevels.add(vl.getId());
            }
        });
        if (!inconsistentVoltageLevels.isEmpty()) {
            resultContext.setVoltageLevelsWithWrongIsc(inconsistentVoltageLevels);
            throw new ShortCircuitException(INCONSISTENT_VOLTAGE_LEVELS, "Some voltage levels have wrong isc values. Check out the logs to find which ones");
        }
    }

    @Override
    protected void sendResultMessage(AbstractResultContext<ShortCircuitRunContext> resultContext, ShortCircuitAnalysisResult result) {
        ShortCircuitRunContext context = resultContext.getRunContext();
        String busId = context.getBusId();
        Map<String, Object> additionalHeaders = new HashMap<>();
        additionalHeaders.put(HEADER_BUS_ID, busId);

        if (result != null && !result.getFaultResults().isEmpty() && resultContext.getRunContext().getBusId() == null &&
                result.getFaultResults().stream().map(FaultResult::getStatus).allMatch(FaultResult.Status.NO_SHORT_CIRCUIT_DATA::equals)) {
            throw new ShortCircuitException(MISSING_EXTENSION_DATA, "Missing short-circuit extension data");
        }

        notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                resultContext.getRunContext().getUserId(), additionalHeaders);
    }

    @Override
    protected Map<String, Object> getDebugHeaders(AbstractResultContext<ShortCircuitRunContext> resultContext, String messageError) {
        Map<String, Object> debugHeaders = super.getDebugHeaders(resultContext, messageError);

        // --- attach bus id to distinct between a ONE_BUS and ALL_BUSES computation type
        debugHeaders.put(HEADER_BUS_ID, resultContext.getRunContext().getBusId());
        return debugHeaders;
    }

    @Override
    protected CompletableFuture<ShortCircuitAnalysisResult> getCompletableFuture(ShortCircuitRunContext runContext, String provider, UUID resultUuid) {
        List<Fault> faults = runContext.getBusId() == null ? getAllBusfaultFromNetwork(runContext) : getBusFaultFromBusId(runContext);
        ShortCircuitParameters parameters = runContext.getParameters();
        if (runContext.getDebugDir() != null) {
            parameters.setDebugDir(runContext.getDebugDir().toString());
        }
        return ShortCircuitAnalysis.runAsync(runContext.getNetwork(), faults, parameters, executionService.getComputationManager(), List.of(), runContext.getReportNode());
    }

    private List<Fault> getAllBusfaultFromNetwork(ShortCircuitRunContext context) {
        Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();
        List<Fault> faults = context.getNetwork().getBusView().getBusStream()
                .map(bus -> {
                    IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = bus.getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
                    if (shortCircuitExtension != null) {
                        shortCircuitLimits.put(bus.getId(), new ShortCircuitLimits(bus.getVoltageLevel().getId(), shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
                    }
                    return new BusFault(bus.getId(), bus.getId());
                })
                .collect(Collectors.toList());
        context.setShortCircuitLimits(shortCircuitLimits);
        return faults;
    }

    private List<Fault> getBusFaultFromBusId(ShortCircuitRunContext context) {
        String busId = context.getBusId();
        Identifiable<?> identifiable = context.getNetwork().getIdentifiable(busId);
        Map<String, ShortCircuitLimits> shortCircuitLimits = new HashMap<>();

        if (identifiable instanceof BusbarSection busbarSection) {
            Bus bus = busbarSection.getTerminal().getBusView().getBus();
            if (bus == null) {
                throw new ShortCircuitException(BUS_OUT_OF_VOLTAGE, "Selected bus is out of voltage");
            }
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = ((BusbarSection) identifiable).getTerminal().getBusView().getBus().getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null) {
                shortCircuitLimits.put(bus.getId(), new ShortCircuitLimits(bus.getVoltageLevel().getId(), shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
            }
            context.setShortCircuitLimits(shortCircuitLimits);
            return List.of(new BusFault(bus.getId(), bus.getId()));
        }

        if (identifiable instanceof Bus bus) {
            String busIdFromBusView = bus.getVoltageLevel().getBusView().getMergedBus(busId).getId();
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = bus.getVoltageLevel().getBusView().getMergedBus(busId).getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null) {
                shortCircuitLimits.put(busIdFromBusView, new ShortCircuitLimits(bus.getVoltageLevel().getId(), shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
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

    @Override
    public void postRun(ShortCircuitRunContext runContext, AtomicReference<ReportNode> rootReportNode, ShortCircuitAnalysisResult ignoredResult) {
        if (runContext.getReportInfos().reportUuid() != null) {
            final ReportNode report = this.reportMapper.map(rootReportNode.get(), runContext);
            observer.observe("report.send", runContext, () -> reportService.sendReport(runContext.getReportInfos().reportUuid(), report));
        }
    }

    @Override
    protected void handleNonCancellationException(AbstractResultContext<ShortCircuitRunContext> resultContext, Exception exception, AtomicReference<ReportNode> rootReporter) {
        if (exception instanceof ShortCircuitException shortCircuitException && shortCircuitException.getType() == INCONSISTENT_VOLTAGE_LEVELS) {
            postRun(resultContext.getRunContext(), rootReporter, null);
            sendResultMessage(resultContext, null);
        }
        resultService.insertStatus(Collections.singletonList(resultContext.getResultUuid()), ShortCircuitAnalysisStatus.FAILED);
    }
}
