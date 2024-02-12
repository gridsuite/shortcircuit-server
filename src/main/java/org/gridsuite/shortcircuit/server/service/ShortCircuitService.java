/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.ws.commons.LogUtils;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.gridsuite.shortcircuit.server.ShortCircuitException;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.repositories.ShortCircuitAnalysisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.shortcircuit.server.ShortCircuitException.Type.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitService.class);

    @Autowired
    NotificationService notificationService;

    private UuidGeneratorService uuidGeneratorService;

    private ShortCircuitAnalysisResultRepository resultRepository;

    private ObjectMapper objectMapper;

    public ShortCircuitService(NotificationService notificationService, UuidGeneratorService uuidGeneratorService, ShortCircuitAnalysisResultRepository resultRepository, ObjectMapper objectMapper) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(ShortCircuitRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), ShortCircuitAnalysisStatus.RUNNING.name());
        notificationService.sendRunMessage(new ShortCircuitResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static ShortCircuitAnalysisResult fromEntity(ShortCircuitAnalysisResultEntity resultEntity, FaultResultsMode mode) {
        List<FaultResult> faultResults = new ArrayList<>();
        switch (mode) {
            case BASIC, FULL:
                faultResults = resultEntity.getFaultResults().stream().map(fr -> fromEntity(fr, mode)).toList();
                break;
            case WITH_LIMIT_VIOLATIONS:
                faultResults = resultEntity.getFaultResults().stream().filter(fr -> !fr.getLimitViolations().isEmpty()).map(fr -> fromEntity(fr, mode)).toList();
                break;
            case NONE:
            default:
                break;
        }
        return new ShortCircuitAnalysisResult(resultEntity.getResultUuid(), resultEntity.getWriteTimeStamp(), faultResults);
    }

    private static FaultResult fromEntity(FaultResultEntity faultResultEntity, FaultResultsMode mode) {
        Fault fault = fromEntity(faultResultEntity.getFault());
        double current = faultResultEntity.getCurrent();
        double positiveMagnitude = faultResultEntity.getPositiveMagnitude();
        double shortCircuitPower = faultResultEntity.getShortCircuitPower();
        ShortCircuitLimits shortCircuitLimits = new ShortCircuitLimits(faultResultEntity.getIpMin(), faultResultEntity.getIpMax(), faultResultEntity.getDeltaCurrentIpMin(), faultResultEntity.getDeltaCurrentIpMax());
        List<LimitViolation> limitViolations = new ArrayList<>();
        List<FeederResult> feederResults = new ArrayList<>();
        if (mode != FaultResultsMode.BASIC) {
            // if we enter here, by calling the getters, the limit violations and feeder results will be loaded even if we don't want to in some mode
            limitViolations = faultResultEntity.getLimitViolations().stream().map(ShortCircuitService::fromEntity).toList();
            feederResults = faultResultEntity.getFeederResults().stream().map(ShortCircuitService::fromEntity).toList();
        }
        return new FaultResult(fault, current, positiveMagnitude, shortCircuitPower, limitViolations, feederResults, shortCircuitLimits);
    }

    private static Fault fromEntity(FaultEmbeddable faultEmbeddable) {
        return new Fault(faultEmbeddable.getId(), faultEmbeddable.getElementId(), faultEmbeddable.getFaultType().name());
    }

    private static LimitViolation fromEntity(LimitViolationEmbeddable limitViolationEmbeddable) {
        return new LimitViolation(limitViolationEmbeddable.getSubjectId(), limitViolationEmbeddable.getLimitType().name(),
                limitViolationEmbeddable.getLimit(), limitViolationEmbeddable.getLimitName(), limitViolationEmbeddable.getValue());
    }

    private static FeederResult fromEntity(FeederResultEntity feederResultEntity) {
        return new FeederResult(feederResultEntity.getConnectableId(), feederResultEntity.getCurrent(), feederResultEntity.getPositiveMagnitude());
    }

    private static ShortCircuitAnalysisResultEntity sortByElementId(ShortCircuitAnalysisResultEntity result) {
        result.setFaultResults(result.getFaultResults().stream()
                .sorted(Comparator.comparing(fr -> fr.getFault().getElementId()))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return result;
    }

    public byte[] exportToCsv(ShortCircuitAnalysisResult result, List<String> headersList, Map<String, String> enumValueTranslations) {
        List<FaultResult> faultResults = result.getFaults();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("shortCircuit_result.csv"));

            // Write UTF-8 BOM
            zipOutputStream.write(0xef);
            zipOutputStream.write(0xbb);
            zipOutputStream.write(0xbf);

            CsvWriterSettings settings = new CsvWriterSettings();
            CsvWriter csvWriter = new CsvWriter(zipOutputStream, settings);

            // Write headers to the CSV file
            csvWriter.writeHeaders(headersList);

            // Write data to the CSV file
            for (FaultResult faultResult : faultResults) {
                // Process faultResult data
                List<String> faultRowData = new ArrayList<>(List.of(
                        faultResult.getFault().getId(),
                        enumValueTranslations.getOrDefault(faultResult.getFault().getFaultType(), ""),
                        "",
                        Double.isNaN(faultResult.getPositiveMagnitude()) ? "" : Double.toString(faultResult.getPositiveMagnitude())
                ));

                List<LimitViolation> limitViolations = faultResult.getLimitViolations();
                if (!limitViolations.isEmpty()) {
                    String limitTypes = limitViolations.stream()
                            .map(LimitViolation::getLimitType)
                            .map(type -> enumValueTranslations.getOrDefault(type, ""))
                            .collect(Collectors.joining(", "));
                    faultRowData.add(limitTypes);
                } else {
                    faultRowData.add("");
                }

                ShortCircuitLimits shortCircuitLimits = faultResult.getShortCircuitLimits();
                faultRowData.addAll(List.of(
                        Double.toString(shortCircuitLimits.getIpMin() / 1000.0),
                        Double.toString(shortCircuitLimits.getIpMax() / 1000.0),
                        Double.toString(faultResult.getShortCircuitPower()),
                        Double.toString(shortCircuitLimits.getDeltaCurrentIpMin()),
                        Double.toString(shortCircuitLimits.getDeltaCurrentIpMax())
                ));

                csvWriter.writeRow(faultRowData);

                // Process feederResults data
                List<FeederResult> feederResults = faultResult.getFeederResults();
                if (!feederResults.isEmpty()) {
                    for (FeederResult feederResult : feederResults) {
                        List<String> feederRowData = new ArrayList<>(List.of(
                                faultResult.getFault().getId(),
                                "",
                                feederResult.getConnectableId(),
                                Double.isNaN(feederResult.getPositiveMagnitude()) ? "" : Double.toString(feederResult.getPositiveMagnitude())
                        ));
                        csvWriter.writeRow(feederRowData);
                    }
                } else {
                    csvWriter.writeRow(List.of("", "", "", ""));
                }
            }

            csvWriter.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new ShortCircuitException(FILE_EXPORT_ERROR, e.getMessage());
        }
    }

    public byte[] getZippedCsvExportResult(UUID resultUuid, CsvTranslation csvTranslation) {
        ShortCircuitAnalysisResult result = getResult(resultUuid, FaultResultsMode.FULL);
        if (result == null) {
            throw new ShortCircuitException(RESULT_NOT_FOUND, "The short circuit analysis result '" + resultUuid + "' does not exist");
        }
        if (Objects.isNull(csvTranslation) || Objects.isNull(csvTranslation.headersCsv()) || Objects.isNull(csvTranslation.enumValueTranslations())) {
            throw new ShortCircuitException(INVALID_EXPORT_PARAMS, "Missing information to export short-circuit result as csv: file headers and enum translation must be provided");
        }
        List<String> headersList = csvTranslation.headersCsv();
        Map<String, String> enumValueTranslations = csvTranslation.enumValueTranslations();
        return exportToCsv(result, headersList, enumValueTranslations);
    }

    public ShortCircuitAnalysisResult getResult(UUID resultUuid, FaultResultsMode mode) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result;
        switch (mode) {
            case BASIC:
                result = resultRepository.findWithFaultResults(resultUuid);
                break;
            case FULL:
                result = resultRepository.findFullResults(resultUuid);
                break;
            case WITH_LIMIT_VIOLATIONS:
                result = resultRepository.findResultsWithLimitViolations(resultUuid);
                break;
            case NONE:
            default:
                result = resultRepository.find(resultUuid);
                break;
        }
        if (result.isPresent()) {
            ShortCircuitAnalysisResultEntity sortedResult = sortByElementId(result.get());

            ShortCircuitAnalysisResult res = fromEntity(sortedResult, mode);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
            }
            return res;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FaultResult> getFaultResultsPage(UUID resultUuid, FaultResultsMode mode, List<ResourceFilter> resourceFilters, Pageable pageable) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result;
        // get without faultResults : FaultResultsM.NONE
        result = resultRepository.find(resultUuid);
        if (result.isPresent()) {
            Page<FaultResultEntity> faultResultEntitiesPage = Page.empty();
            switch (mode) {
                case BASIC, FULL:
                    faultResultEntitiesPage = resultRepository.findFaultResultsPage(result.get(), resourceFilters, pageable, mode);
                    break;
                case WITH_LIMIT_VIOLATIONS:
                    faultResultEntitiesPage = resultRepository.findFaultResultsWithLimitViolationsPage(result.get(), resourceFilters, pageable);
                    break;
                case NONE:
                default:
                    break;
            }
            Page<FaultResult> faultResultsPage = faultResultEntitiesPage.map(fr -> fromEntity(fr, mode));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return faultResultsPage;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FeederResult> getFeederResultsPage(UUID resultUuid, List<ResourceFilter> resourceFilters, Pageable pageable) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result = resultRepository.find(resultUuid);
        if (result.isPresent()) {
            Page<FeederResultEntity> feederResultEntitiesPage = resultRepository.findFeederResultsPage(result.get(), resourceFilters, pageable);
            Page<FeederResult> feederResultsPage = feederResultEntitiesPage.map(ShortCircuitService::fromEntity);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Get ShortCircuit Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return feederResultsPage;
        }
        return null;
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        resultRepository.insertStatus(resultUuids, status);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new ShortCircuitCancelContext(resultUuid, receiver).toMessage());
    }
}
