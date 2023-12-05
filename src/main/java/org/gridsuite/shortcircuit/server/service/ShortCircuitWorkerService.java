/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitLimits;
import org.gridsuite.shortcircuit.server.reports.AbstractReportMapper;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.shortcircuit.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.shortcircuit.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitWorkerService.class);
    private static final String SHORTCIRCUIT_ALL_BUSES_DEFAULT_TYPE_REPORT = "AllBusesShortCircuitAnalysis";
    private static final String SHORTCIRCUIT_ONE_BUS_DEFAULT_TYPE_REPORT = "OneBusShortCircuitAnalysis";

    private NetworkStoreService networkStoreService;
    private ReportService reportService;
    private ShortCircuitAnalysisResultRepository resultRepository;
    private NotificationService notificationService;
    private ObjectMapper objectMapper;
    private final Collection<AbstractReportMapper> reportMappers;

    private Map<UUID, CompletableFuture<ShortCircuitAnalysisResult>> futures = new ConcurrentHashMap<>();

    private Map<UUID, ShortCircuitCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelShortCircuitAnalysis = new ReentrantLock();

    @Autowired
    public ShortCircuitWorkerService(NetworkStoreService networkStoreService, ReportService reportService,
                                     NotificationService notificationService, ShortCircuitAnalysisResultRepository resultRepository,
                                     ObjectMapper objectMapper, Collection<AbstractReportMapper> reportMappers) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.reportService = Objects.requireNonNull(reportService);
        this.notificationService = Objects.requireNonNull(notificationService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.reportMappers = Objects.requireNonNull(reportMappers);
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    private ShortCircuitAnalysisResult run(ShortCircuitRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(context);

        LOGGER.info("Run short circuit analysis...");
        Network network = getNetwork(context.getNetworkUuid(), context.getVariantId());

        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportUuid() != null) {
            String reportType = context.getReportType();
            if (StringUtils.isEmpty(reportType)) {
                reportType = StringUtils.isEmpty(context.getBusId()) ? SHORTCIRCUIT_ALL_BUSES_DEFAULT_TYPE_REPORT : SHORTCIRCUIT_ONE_BUS_DEFAULT_TYPE_REPORT;
            }
            String rootReporterId = context.getReporterId() == null ? reportType : context.getReporterId() + "@" + reportType;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(reportType, reportType + " (${providerToUse})", "providerToUse", ShortCircuitAnalysis.find().getName());
            // Delete any previous short-circuit computation logs
            reportService.deleteReport(context.getReportUuid(), reportType);
        }

        CompletableFuture<ShortCircuitAnalysisResult> future = runShortCircuitAnalysisAsync(context, network, reporter, resultUuid);

        ShortCircuitAnalysisResult result = future == null ? null : future.get();
        if (context.getReportUuid() != null) {
            for (final AbstractReportMapper reportMapper : reportMappers) {
                rootReporter = reportMapper.processReporter(rootReporter);
            }
            reportService.sendReport(context.getReportUuid(), rootReporter);
        }
        return result;
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

        if (identifiable instanceof BusbarSection) {
            String busIdFromBusView = ((BusbarSection) identifiable).getTerminal().getBusView().getBus().getId();
            IdentifiableShortCircuit<VoltageLevel> shortCircuitExtension = ((BusbarSection) identifiable).getTerminal().getBusView().getBus().getVoltageLevel().getExtension(IdentifiableShortCircuit.class);
            if (shortCircuitExtension != null) {
                shortCircuitLimits.put(busIdFromBusView, new ShortCircuitLimits(shortCircuitExtension.getIpMin(), shortCircuitExtension.getIpMax()));
            }
            context.setShortCircuitLimits(shortCircuitLimits);
            return List.of(new BusFault(busIdFromBusView, busIdFromBusView));
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

    private CompletableFuture<ShortCircuitAnalysisResult> runShortCircuitAnalysisAsync(ShortCircuitRunContext context,
                                                                                       Network network,
                                                                                       Reporter reporter,
                                                                                       UUID resultUuid) {
        lockRunAndCancelShortCircuitAnalysis.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }

            List<Fault> faults = context.getBusId() == null
                ? getAllBusfaultFromNetwork(network, context)
                : getBusFaultFromBusId(network, context);

            CompletableFuture<ShortCircuitAnalysisResult> future = ShortCircuitAnalysis.runAsync(
                network,
                faults,
                context.getParameters(),
                LocalComputationManager.getDefault(),
                List.of(),
                reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancelShortCircuitAnalysis.unlock();
        }
    }

    private void cancelShortCircuitAnalysisAsync(ShortCircuitCancelContext cancelContext) {
        lockRunAndCancelShortCircuitAnalysis.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<ShortCircuitAnalysisResult> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanShortCircuitAnalysisResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancelShortCircuitAnalysis.unlock();
        }
    }

    private void cleanShortCircuitAnalysisResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver);
        LOGGER.info(CANCEL_MESSAGE + " (resultUuid='{}')", resultUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            ShortCircuitResultContext resultContext = ShortCircuitResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                ShortCircuitAnalysisResult result = run(resultContext.getRunContext(), resultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                resultRepository.insert(resultContext.getResultUuid(), result, resultContext.getRunContext().getShortCircuitLimits(), ShortCircuitAnalysisStatus.COMPLETED.name());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    if (!result.getFaultResults().isEmpty() &&
                        result.getFaultResults().stream().map(FaultResult::getStatus).allMatch(FaultResult.Status.NO_SHORT_CIRCUIT_DATA::equals)) {
                        LOGGER.error("Short circuit analysis failed (resultUuid='{}')", resultContext.getResultUuid());
                        notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                                "Missing short-circuit extension data",
                                resultContext.getRunContext().getUserId(), resultContext.getRunContext().getBusId());
                    } else {
                        notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), resultContext.getRunContext().getBusId());
                        LOGGER.info("Short circuit analysis complete (resultUuid='{}')", resultContext.getResultUuid());
                    }
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanShortCircuitAnalysisResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(FAIL_MESSAGE, e);
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage(), resultContext.getRunContext().getUserId(), resultContext.getRunContext().getBusId());
                    resultRepository.delete(resultContext.getResultUuid());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelShortCircuitAnalysisAsync(ShortCircuitCancelContext.fromMessage(message));
    }
}
