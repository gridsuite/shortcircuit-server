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
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.shortcircuit.*;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitAnalysisStatus;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.shortcircuit.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitWorkerService.class);

    private NetworkStoreService networkStoreService;

    private ReportService reportService;

    private ShortCircuitAnalysisResultRepository resultRepository;

    private ObjectMapper objectMapper;

    private Map<UUID, CompletableFuture<ShortCircuitAnalysisResult>> futures = new ConcurrentHashMap<>();

    private Map<UUID, ShortCircuitCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelShortCircuitAnalysis = new ReentrantLock();

    @Autowired
    NotificationService notificationService;

    public ShortCircuitWorkerService(NetworkStoreService networkStoreService, ReportService reportService,
                                     ShortCircuitAnalysisResultRepository resultRepository, ObjectMapper objectMapper) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.reportService = Objects.requireNonNull(reportService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    private Network getNetwork(UUID networkUuid, List<UUID> otherNetworkUuids, String variantId) {
        Network network = getNetwork(networkUuid, variantId);
        if (otherNetworkUuids.isEmpty()) {
            return network;
        } else {
            List<Network> otherNetworks = otherNetworkUuids.stream().map(uuid -> getNetwork(uuid, variantId)).collect(Collectors.toList());
            List<Network> networks = new ArrayList<>();
            networks.add(network);
            networks.addAll(otherNetworks);
            MergingView mergingView = MergingView.create("merge", "iidm");
            mergingView.merge(networks.toArray(new Network[0]));
            return mergingView;
        }
    }

    public ShortCircuitAnalysisResult run(ShortCircuitRunContext context) {
        try {
            return run(context, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.error(FAIL_MESSAGE, e);
            return null;
        }
    }

    private ShortCircuitAnalysisResult run(ShortCircuitRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(context);

        LOGGER.info("Run short circuit analysis...");
        Network network = getNetwork(context.getNetworkUuid(), context.getOtherNetworkUuids(), context.getVariantId());

        Reporter reporter = context.getReportUuid() != null ? new ReporterModel("ShortCircuitAnalysis", "Short circuit analysis") : Reporter.NO_OP;

        CompletableFuture<ShortCircuitAnalysisResult> future = runShortCircuitAnalysisAsync(context, network, reporter, resultUuid);

        ShortCircuitAnalysisResult result = future == null ? null : future.get();
        if (context.getReportUuid() != null) {
            reportService.sendReport(context.getReportUuid(), reporter);
        }
        return result;
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

            CompletableFuture<ShortCircuitAnalysisResult> future = ShortCircuitAnalysis.runAsync(
                network,
                List.of(),
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

                resultRepository.insert(resultContext.getResultUuid(), result);
                resultRepository.insertStatus(List.of(resultContext.getResultUuid()), ShortCircuitAnalysisStatus.COMPLETED.name());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("Short circuit analysis complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanShortCircuitAnalysisResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error(FAIL_MESSAGE, e);
                if (!(e instanceof CancellationException)) {
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage());
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
