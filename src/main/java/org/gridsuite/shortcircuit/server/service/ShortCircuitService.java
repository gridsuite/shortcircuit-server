/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import com.powsybl.ws.commons.LogUtils;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.gridsuite.shortcircuit.server.ShortCircuitException;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.*;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.computation.utils.FilterUtils.fromStringFiltersToDTO;
import static org.gridsuite.shortcircuit.server.ShortCircuitException.Type.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitService extends AbstractComputationService<ShortCircuitRunContext, ShortCircuitAnalysisResultService, ShortCircuitAnalysisStatus> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitService.class);
    public static final String GET_SHORT_CIRCUIT_RESULTS_MSG = "Get ShortCircuit Results {} in {}ms";
    public static final char CSV_DELIMITER_FR = ';';
    public static final char CSV_DELIMITER_EN = ',';
    public static final char CSV_QUOTE_ESCAPE = '"';

    // This voltage intervals' definition is not clean and we could potentially lose some buses.
    // To be cleaned when VoltageRange uses intervals that are open on the right.
    // TODO: to be moved to RTE private config or to powsybl-rte-core
    public static final List<VoltageRange> CEI909_VOLTAGE_PROFILE = List.of(
            new VoltageRange(0, 199.999, 1.1),
            new VoltageRange(200.0, 299.999, 1.09),
            new VoltageRange(300.0, 389.99, 1.10526),
            new VoltageRange(390.0, 410.0, 1.05)
    );

    private final ParametersRepository parametersRepository;
    private final FilterService filterService;

    public ShortCircuitService(final NotificationService notificationService,
                               final UuidGeneratorService uuidGeneratorService,
                               final ShortCircuitAnalysisResultService resultService,
                               @Autowired(required = false)
                               ComputationS3Service computationS3Service,
                               final ParametersRepository parametersRepository,
                               final FilterService filterService,
                               final ObjectMapper objectMapper) {
        super(notificationService, resultService, computationS3Service, objectMapper, uuidGeneratorService, null);
        this.parametersRepository = parametersRepository;
        this.filterService = filterService;
    }

    @Transactional
    public UUID runAndSaveResult(UUID networkUuid, String variantId, String receiver, UUID reportUuid, String reporterId, String reportType,
                                 String userId, String busId, boolean debug, final Optional<UUID> parametersUuid) {
        ShortCircuitParameters parameters = fromEntity(parametersUuid.flatMap(parametersRepository::findById).orElseGet(ShortCircuitParametersEntity::new)).parameters();
        parameters.setWithFortescueResult(StringUtils.isNotBlank(busId));
        parameters.setDetailedReport(false);
        return runAndSaveResult(new ShortCircuitRunContext(networkUuid, variantId, receiver, parameters, reportUuid, reporterId, reportType, userId,
            "default-provider", // TODO : replace with null when fix in powsybl-ws-commons will handle null provider
            busId, debug));
    }

    @Override
    public UUID runAndSaveResult(ShortCircuitRunContext runContext) {
        Objects.requireNonNull(runContext);
        final UUID resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), ShortCircuitAnalysisStatus.RUNNING);
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
        return new Fault(faultEmbeddable.getId(), faultEmbeddable.getElementId(), faultEmbeddable.getVoltageLevelId(), faultEmbeddable.getFaultType().name());
    }

    private static LimitViolation fromEntity(LimitViolationEmbeddable limitViolationEmbeddable) {
        return new LimitViolation(limitViolationEmbeddable.getSubjectId(), limitViolationEmbeddable.getLimitType().name(),
                limitViolationEmbeddable.getLimit(), limitViolationEmbeddable.getLimitName(), limitViolationEmbeddable.getValue());
    }

    private static FeederResult fromEntity(FeederResultEntity feederResultEntity) {
        return new FeederResult(feederResultEntity.getConnectableId(), feederResultEntity.getCurrent(), feederResultEntity.getPositiveMagnitude(), feederResultEntity.getSide() != null ? feederResultEntity.getSide().name() : null);
    }

    private static ShortCircuitParametersEntity toEntity(ShortCircuitParametersInfos parametersInfos) {
        final ShortCircuitParameters parameters = parametersInfos.parameters();
        return new ShortCircuitParametersEntity(
            parameters.isWithLimitViolations(),
            parameters.isWithVoltageResult(),
            parameters.isWithFeederResult(),
            parameters.getStudyType(),
            parameters.getMinVoltageDropProportionalThreshold(),
            parametersInfos.predefinedParameters(),
            parameters.isWithLoads(),
            parameters.isWithShuntCompensators(),
            parameters.isWithVSCConverterStations(),
            parameters.isWithNeutralPosition(),
            parameters.getInitialVoltageProfileMode()
        );
    }

    private static ShortCircuitParametersInfos fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return new ShortCircuitParametersInfos(
            entity.getPredefinedParameters(),
            new ShortCircuitParameters()
                .setStudyType(entity.getStudyType())
                .setMinVoltageDropProportionalThreshold(entity.getMinVoltageDropProportionalThreshold())
                .setWithFeederResult(entity.isWithFeederResult())
                .setWithLimitViolations(entity.isWithLimitViolations())
                .setWithVoltageResult(entity.isWithVoltageResult())
                .setWithLoads(entity.isWithLoads())
                .setWithShuntCompensators(entity.isWithShuntCompensators())
                .setWithVSCConverterStations(entity.isWithVscConverterStations())
                .setWithNeutralPosition(entity.isWithNeutralPosition())
                .setInitialVoltageProfileMode(entity.getInitialVoltageProfileMode())
                // the voltageRanges is not taken into account when initialVoltageProfileMode=NOMINAL
                .setVoltageRanges(InitialVoltageProfileMode.CONFIGURED.equals(entity.getInitialVoltageProfileMode()) ? CEI909_VOLTAGE_PROFILE : null)
        );
    }

    private static ShortCircuitAnalysisResultEntity sortByElementId(ShortCircuitAnalysisResultEntity result) {
        result.setFaultResults(result.getFaultResults().stream()
                .sorted(Comparator.comparing(fr -> fr.getFault().getElementId()))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return result;
    }

    private static void setFormat(CsvFormat format, String language) {
        format.setLineSeparator(System.lineSeparator());
        format.setDelimiter(language != null && language.equals("fr") ? CSV_DELIMITER_FR : CSV_DELIMITER_EN);
        format.setQuoteEscape(CSV_QUOTE_ESCAPE);
    }

    private static String convertDoubleToLocale(Double value, String language) {
        NumberFormat nf = NumberFormat.getInstance(language != null && language.equals("fr") ? Locale.FRENCH : Locale.US);
        nf.setGroupingUsed(false);
        return nf.format(value);
    }

    private void addLimitsRow(CsvWriter csvWriter, FaultResult faultResult, CsvExportParams csvExportParams, int faultNumber) {
        String language = csvExportParams.language();
        Map<String, String> enumValueTranslations = csvExportParams.enumValueTranslations();
        String faultResultId = faultResult.getFault().getId();
        double faultCurrentValue = (faultNumber == 1 ? faultResult.getPositiveMagnitude() : faultResult.getCurrent()) / 1000.0;
        String faultCurrentValueStr = Double.isNaN(faultCurrentValue) ? "" : convertDoubleToLocale(faultCurrentValue, language);

        // Process faultResult data
        List<String> faultRowData = new ArrayList<>(List.of(
                faultResultId,
                faultResult.getFault().getVoltageLevelId() != null ? faultResult.getFault().getVoltageLevelId() : "",
                enumValueTranslations.getOrDefault(faultResult.getFault().getFaultType(), ""),
                "", // feeder
                faultCurrentValueStr // Isc
        ));
        if (csvExportParams.oneBusCase()) {
            faultRowData.add(""); // side (extra 1-bus mode column)
        }
        // limit type column (N comma-separated values)
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
                convertDoubleToLocale(shortCircuitLimits.getIpMin() / 1000.0, language),
                convertDoubleToLocale(shortCircuitLimits.getIpMax() / 1000.0, language),
                convertDoubleToLocale(faultResult.getShortCircuitPower(), language),
                convertDoubleToLocale(shortCircuitLimits.getDeltaCurrentIpMin() / 1000.0, language),
                convertDoubleToLocale(shortCircuitLimits.getDeltaCurrentIpMax() / 1000.0, language)
        ));

        csvWriter.writeRow(faultRowData);
    }

    private void addFeedersRows(CsvWriter csvWriter, FaultResult faultResult, CsvExportParams csvExportParams, int faultNumber) {
        String language = csvExportParams.language();
        Map<String, String> enumValueTranslations = csvExportParams.enumValueTranslations();
        String faultResultId = faultResult.getFault().getId();

        List<FeederResult> feederResults = faultResult.getFeederResults();
        if (!feederResults.isEmpty()) {
            for (FeederResult feederResult : feederResults) {
                double feederCurrentValue = (faultNumber == 1 ? feederResult.getPositiveMagnitude() : feederResult.getCurrent()) / 1000.0;
                String feederCurrentValueStr = Double.isNaN(feederCurrentValue) ? "" : convertDoubleToLocale(feederCurrentValue, language);
                String feederSide = feederResult.getSide() != null ? enumValueTranslations.getOrDefault(feederResult.getSide(), "") : ""; // Assuming FeederResult also has a side
                List<String> feederRowData = new ArrayList<>(List.of(
                        faultResultId,
                        "", // VL
                        "", // type
                        feederResult.getConnectableId(),
                        feederCurrentValueStr,
                        feederSide
                ));
                csvWriter.writeRow(feederRowData);
            }
        } else {
            csvWriter.writeRow(List.of("", "", "", "", "", ""));
        }
    }

    public byte[] exportToCsv(ShortCircuitAnalysisResult result, CsvExportParams csvExportParams) {
        List<FaultResult> faultResults = result.getFaults();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            zipOutputStream.putNextEntry(new ZipEntry("shortCircuit_result.csv"));
            // This code is for writing the UTF-8 Byte Order Mark (BOM) to a ZipOutputStream
            // by adding BOM to the beginning of file to help excel in some versions to detect this is UTF-8 encoding bytes
            zipOutputStream.write(0xef);
            zipOutputStream.write(0xbb);
            zipOutputStream.write(0xbf);

            CsvWriterSettings settings = new CsvWriterSettings();
            setFormat(settings.getFormat(), csvExportParams.language());
            CsvWriter csvWriter = new CsvWriter(zipOutputStream, StandardCharsets.UTF_8, settings);
            csvWriter.writeHeaders(csvExportParams.csvHeader());

            // Write data to the CSV file.
            for (FaultResult faultResult : faultResults) {
                addLimitsRow(csvWriter, faultResult, csvExportParams, faultResults.size());
                addFeedersRows(csvWriter, faultResult, csvExportParams, faultResults.size());
            }
            csvWriter.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new ShortCircuitException(FILE_EXPORT_ERROR, e.getMessage());
        }
    }

    public byte[] getZippedCsvExportResult(UUID resultUuid, ShortCircuitAnalysisResult result, CsvExportParams csvExportParams) {
        if (result == null) {
            throw new ShortCircuitException(RESULT_NOT_FOUND, "The short circuit analysis result '" + resultUuid + "' does not exist");
        }
        if (Objects.isNull(csvExportParams) || Objects.isNull(csvExportParams.csvHeader()) || Objects.isNull(csvExportParams.enumValueTranslations())) {
            throw new ShortCircuitException(INVALID_EXPORT_PARAMS, "Missing information to export short-circuit result as csv: file headers and enum translation must be provided");
        }
        return exportToCsv(result, csvExportParams);
    }

    @Transactional(readOnly = true)
    public ShortCircuitAnalysisResult getResult(UUID resultUuid, FaultResultsMode mode) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result = switch (mode) {
            case BASIC -> resultService.findWithFaultResults(resultUuid);
            case FULL -> resultService.findFullResults(resultUuid);
            case WITH_LIMIT_VIOLATIONS -> resultService.findResultsWithLimitViolations(resultUuid);
            default -> resultService.find(resultUuid);
        };
        if (result.isPresent()) {
            ShortCircuitAnalysisResultEntity sortedResult = sortByElementId(result.get());

            ShortCircuitAnalysisResult res = fromEntity(sortedResult, mode);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(GET_SHORT_CIRCUIT_RESULTS_MSG, resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
            }
            return res;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FaultResult> getFaultResultsPage(UUID rootNetworkUuid,
                                                 String variantId,
                                                 UUID resultUuid,
                                                 FaultResultsMode mode,
                                                 String stringFilters,
                                                 GlobalFilter globalFilter,
                                                 Pageable pageable) {
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        List<ResourceFilterDTO> resourceGlobalFilters = new ArrayList<>();
        if (globalFilter != null) {
            Optional<ResourceFilterDTO> resourceGlobalFilter = filterService.getResourceFilter(rootNetworkUuid, variantId, globalFilter);
            // No equipment verify global filters : no result
            if (resourceGlobalFilter.isEmpty()) {
                return Page.empty();
            } else {
                resourceGlobalFilters.add(resourceGlobalFilter.get());
            }
        }
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result;
        // get without faultResults : FaultResultsM.NONE
        result = resultService.find(resultUuid);
        if (result.isPresent()) {
            Page<FaultResultEntity> faultResultEntitiesPage = Page.empty();
            switch (mode) {
                case BASIC, FULL:
                    faultResultEntitiesPage = resultService.findFaultResultsPage(result.get(), resourceFilters, resourceGlobalFilters, pageable, mode);
                    break;
                case WITH_LIMIT_VIOLATIONS:
                    faultResultEntitiesPage = resultService.findFaultResultsWithLimitViolationsPage(result.get(), resourceFilters, resourceGlobalFilters, pageable);
                    break;
                case NONE:
                default:
                    break;
            }
            if (faultResultEntitiesPage.isEmpty()) {
                return Page.empty();
            }
            Page<FaultResult> faultResultsPage = faultResultEntitiesPage.map(fr -> fromEntity(fr, mode));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(GET_SHORT_CIRCUIT_RESULTS_MSG, resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return faultResultsPage;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<FeederResult> getFeederResultsPage(UUID resultUuid, String stringFilters, Pageable pageable) {
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> result = resultService.find(resultUuid);
        if (result.isPresent()) {
            Page<FeederResultEntity> feederResultEntitiesPage = resultService.findFeederResultsPage(result.get(), resourceFilters, pageable);
            if (feederResultEntitiesPage.isEmpty()) {
                return Page.empty();
            }
            Page<FeederResult> feederResultsPage = feederResultEntitiesPage.map(ShortCircuitService::fromEntity);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(GET_SHORT_CIRCUIT_RESULTS_MSG, resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
                LOGGER.info("pageable =  {}", LogUtils.sanitizeParam(pageable.toString()));
            }
            return feederResultsPage;
        }
        return null;
    }

    @Override
    public List<String> getProviders() {
        return List.of();
    }

    public List<LimitViolationType> getLimitTypes(UUID resultUuid) {
        return resultService.findLimitTypes(resultUuid);
    }

    public List<ThreeSides> getBranchSides(UUID resultUuid) {
        return resultService.findBranchSides(resultUuid);
    }

    public List<com.powsybl.shortcircuit.Fault.FaultType> getFaultTypes(UUID resultUuid) {
        return resultService.findFaultTypes(resultUuid);
    }

    public Optional<ShortCircuitParametersInfos> getParameters(final UUID parametersUuid) {
        return parametersRepository.findById(parametersUuid).map(ShortCircuitService::fromEntity);
    }

    @Transactional
    public boolean deleteParameters(final UUID parametersUuid) {
        final boolean result = parametersRepository.existsById(parametersUuid);
        if (result) {
            parametersRepository.deleteById(parametersUuid);
        }
        return result;
    }

    @Transactional
    public Optional<UUID> duplicateParameters(UUID sourceParametersUuid) {
        return parametersRepository.findById(sourceParametersUuid)
                                   .map(ShortCircuitParametersEntity::new)
                                   .map(parametersRepository::save)
                                   .map(ShortCircuitParametersEntity::getId);
    }

    public UUID createParameters(@Nullable final ShortCircuitParametersInfos parameters) {
        return parametersRepository.save(parameters != null ? toEntity(parameters) : new ShortCircuitParametersEntity()).getId();
    }

    @Transactional
    public boolean updateOrResetParameters(final UUID parametersUuid, @Nullable final ShortCircuitParametersInfos givenParameters) {
        return parametersRepository.findById(parametersUuid)
            .map(parameters -> {
                parameters.updateWith(givenParameters != null ? toEntity(givenParameters) : new ShortCircuitParametersEntity());
                return true;
            })
            .orElse(false);
    }
}
