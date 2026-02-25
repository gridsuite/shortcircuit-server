/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.security.LimitViolationType;
import com.powsybl.ws.commons.LogUtils;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.gridsuite.filter.identifierlistfilter.FilterEquipments;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.computation.utils.FilterUtils;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.dto.powsybl_private.PowerElectronicsCluster;
import org.gridsuite.shortcircuit.server.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_EXPORT_PARAMS;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;
import static org.gridsuite.computation.utils.FilterUtils.fromStringFiltersToDTO;

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
    public static final String POWER_ELECTRONICS_CLUSTERS = "powerElectronicsClusters";
    // TODO remove when name fixed in powsybl
    public static final String POWER_ELECTRONICS_CLUSTER = "powerElectronicsCluster";
    public static final String NODE_CLUSTER = "nodeCluster";

    private final FilterService filterService;

    private final ShortCircuitParametersService parametersService;

    public ShortCircuitService(final NotificationService notificationService,
                               final UuidGeneratorService uuidGeneratorService,
                               final ShortCircuitAnalysisResultService resultService,
                               @Autowired(required = false)
                               ComputationS3Service computationS3Service,
                               final FilterService filterService,
                               final ShortCircuitParametersService parametersService,
                               @Value("${shortcircuit-analysis.default-provider}") String defaultProvider,
                               final ObjectMapper objectMapper) {
        super(notificationService, resultService, computationS3Service, objectMapper, uuidGeneratorService, defaultProvider);
        this.filterService = filterService;
        this.parametersService = parametersService;
    }

    private List<Object> deserializePowerElectronicsClusters(String powerElectronicsClustersValue, UUID networkUuid, String variantId) throws IOException {
        // Normalize specific parameters: for "powerElectronicsClusters" convert objects that contain a
        // "filterUuids" entry (List<UUID>) into objects containing "equipmentIds" (String[]).
        if (powerElectronicsClustersValue == null) {
            return Collections.emptyList();
        }

        // parse into typed list
        List<PowerElectronicsCluster> clusters = objectMapper.readValue(powerElectronicsClustersValue, new TypeReference<List<PowerElectronicsCluster>>() { });

        // filter by active one only and get all filterUuids
        List<PowerElectronicsCluster> activeClusters = clusters.stream()
            .filter(c -> c.isActive())
            .toList();
        List<UUID> filterUuids = activeClusters.stream()
            .flatMap(item -> item.getFilters().stream().map(FilterElements::getFilterId))
            .toList();

        // Apply filters using filterService
        List<FilterEquipments> filterEquipments = filterService.getFilterEquipments(filterUuids, networkUuid, variantId);

        // regroup by filterIds in clusters list to get equipmentIds
        Map<UUID, List<String>> filterIdToEquipmentIds = filterEquipments.stream()
                .collect(Collectors.toMap(
                        FilterEquipments::getFilterId,
                        fe -> fe.getIdentifiableAttributes()
                                .stream()
                                .map(IdentifiableAttributes::getId)
                                .toList()
                ));
        // replace filterUuids by equipmentIds in clusters
        List<Object> normalizedClusters = new ArrayList<>();
        int index = 0;
        for (PowerElectronicsCluster cluster : activeClusters) {
            Map<String, Object> normalizedCluster = new HashMap<>();
            normalizedCluster.put("id", Integer.toString(index++));
            normalizedCluster.put("alpha", cluster.getAlpha());
            normalizedCluster.put("u0", cluster.getU0());
            normalizedCluster.put("usMin", cluster.getUsMin());
            normalizedCluster.put("usMax", cluster.getUsMax());
            normalizedCluster.put("type", cluster.getType());
            // get equipmentIds from filterIds
            Set<String> equipmentIds = cluster.getFilters().stream()
                .map(FilterElements::getFilterId)
                .map(filterIdToEquipmentIds::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
            normalizedCluster.put("equipmentIds", equipmentIds);
            // serialize the normalizedCluster map to a JSON string
            normalizedClusters.add(normalizedCluster);
        }
        // Replace single quotes with double quotes
        return normalizedClusters;
    }

    private List<String> deserializeInCalculationClusterFilters(String inCalculationClusterFiltersValue, UUID networkUuid, String variantId, Network network) throws IOException {
        List<FilterElements> filterData = objectMapper.readValue(inCalculationClusterFiltersValue, new TypeReference<List<FilterElements>>() { });
        List<UUID> filterUuids = filterData.stream()
                .map(FilterElements::getFilterId)
                .toList();

        // Apply filters using filterService
        List<IdentifiableAttributes> filterIdentifiables = filterService.getIdentifiablesFromFilters(filterUuids, networkUuid, variantId);

        // get bus Ids from IdentifiableAttributes
        List<String> busIds = new ArrayList<>();
        filterIdentifiables.forEach(identifiableAttributes -> {
            VoltageLevel voltageLevel = network.getVoltageLevel(identifiableAttributes.getId());
            if (voltageLevel != null) {
                voltageLevel.getBusView().getBusStream().forEach(bus -> busIds.add(bus.getId()));
            }
        });
        return busIds;
    }

    private Map<String, String> deserializeSpecificParameters(Map<String, String> specificParameters, UUID networkUuid, String variantId, Network network) {
        // This is defensive: we check types at runtime and only transform when the expected shape is present.
        try {
            if (specificParameters != null) {
                if (specificParameters.containsKey(POWER_ELECTRONICS_CLUSTERS)) {
                    List<Object> powerElectronicsClustersValue = deserializePowerElectronicsClusters(specificParameters.get(POWER_ELECTRONICS_CLUSTERS), networkUuid, variantId);
                    specificParameters.remove(POWER_ELECTRONICS_CLUSTERS);
                    specificParameters.put(POWER_ELECTRONICS_CLUSTER, objectMapper.writeValueAsString(powerElectronicsClustersValue));
                }
                if (specificParameters.containsKey(NODE_CLUSTER)) {
                    List<String> inCalculationClusterFiltersValue = deserializeInCalculationClusterFilters(specificParameters.get(NODE_CLUSTER), networkUuid, variantId, network);
                    specificParameters.put(NODE_CLUSTER, objectMapper.writeValueAsString(inCalculationClusterFiltersValue));
                }
            }
        } catch (Exception ex) {
            // avoid breaking the run flow for unexpected shapes; log if you have a logger available
            LOGGER.info("Could not normalize specific parameters for powerElectronicsClusters", ex);
        }
        return specificParameters;
    }

    @Override
    @Transactional
    public UUID runAndSaveResult(ShortCircuitRunContext runContext) {
        Objects.requireNonNull(runContext);
        ShortCircuitParametersValues parameters = runContext.getParametersUuid() != null
            ? parametersService.getParametersValues(runContext.getParametersUuid())
            : parametersService.getDefaultParametersValues();
        parameters.getCommonParameters().setWithFortescueResult(StringUtils.isNotBlank(runContext.getBusId()));
        parameters.getCommonParameters().setDetailedReport(false);

        Map<String, String> translatedSpecificParameters = deserializeSpecificParameters(parameters.getSpecificParameters(), runContext.getNetworkUuid(), runContext.getVariantId(), runContext.getNetwork());
        parameters.setSpecificParameters(translatedSpecificParameters);

        // set provider and parameters
        runContext.setParameters(parameters);
        runContext.setProvider(parameters.getProvider() != null ? parameters.getProvider() : getDefaultProvider());
        final UUID resultUuid = runContext.getResultUuid();

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

    private static FaultResult buildFaultResultFromSomeOfItsFeederResultEntities(List<FeederResultEntity> feederResultEntities) {
        /*
         * Build a FaultResult from a subset of its FeederResultEntities.
         *
         * <p>All feederResultEntities must belong to the same parent FaultResult.
         * The resulting FaultResult will include only the provided feeders, preserving their order.
         */
        if (feederResultEntities == null || feederResultEntities.isEmpty()) {
            return new FaultResult();
        }

        FaultResultEntity faultResultEntity = feederResultEntities.getFirst().getFaultResult();

        if (!feederResultEntities.stream().allMatch(feederResult -> feederResult.getFaultResult().equals(faultResultEntity))) {
            throw new IllegalStateException("All FeederResults must be associated with the same FaultResult");
        }

        Fault fault = fromEntity(faultResultEntity.getFault());
        ShortCircuitLimits shortCircuitLimits = new ShortCircuitLimits(
                faultResultEntity.getIpMin(),
                faultResultEntity.getIpMax(),
                faultResultEntity.getDeltaCurrentIpMin(),
                faultResultEntity.getDeltaCurrentIpMax()
        );
        List<LimitViolation> limitViolations = faultResultEntity.getLimitViolations().stream().map(ShortCircuitService::fromEntity).toList();
        List<FeederResult> feederResults = feederResultEntities.stream().map(ShortCircuitService::fromEntity).toList();

        return new FaultResult(
                fault,
                faultResultEntity.getCurrent(),
                faultResultEntity.getPositiveMagnitude(),
                faultResultEntity.getShortCircuitPower(),
                limitViolations,
                feederResults,
                shortCircuitLimits
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
        }
    }

    public byte[] getZippedCsvExportResult(List<FaultResult> faultResults, CsvExportParams csvExportParams) {
        if (Objects.isNull(csvExportParams) || Objects.isNull(csvExportParams.csvHeader()) || Objects.isNull(csvExportParams.enumValueTranslations())) {
            throw new ComputationException(INVALID_EXPORT_PARAMS, "Missing information to export short-circuit result as csv: file headers and enum translation must be provided");
        }

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
            throw new UncheckedIOException("Error occurred while writing data to csv file", e);
        }
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

    public Map<String, Double> getBasicResultForSpecificEquipment(UUID resultUuid, String voltageLevelId) {
        return resultService.getFaultResultByVoltageLevelId(resultUuid, voltageLevelId).stream().collect(Collectors.toMap(fr -> fr.getFault().getElementId(), FaultResultEntity::getCurrent));
    }

    @Transactional(readOnly = true)
    public Page<FaultResult> getFaultResultsPage(UUID networkUuid,
                                                 String variantId,
                                                 UUID resultUuid,
                                                 FaultResultsMode mode,
                                                 String stringFilters,
                                                 String globalFilters,
                                                 Pageable pageable) {
        String decodedStringGlobalFilters = globalFilters != null ? URLDecoder.decode(globalFilters, StandardCharsets.UTF_8) : null;
        GlobalFilter globalFilter = FilterUtils.fromStringGlobalFiltersToDTO(decodedStringGlobalFilters, objectMapper);
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        List<ResourceFilterDTO> resourceGlobalFilters = new ArrayList<>();
        if (globalFilter != null && !globalFilter.isEmpty()) {
            Optional<ResourceFilterDTO> resourceGlobalFilter = filterService.getResourceFilter(networkUuid, variantId, globalFilter);
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
        if (result.isEmpty()) {
            throw new ComputationException(RESULT_NOT_FOUND, "The short circuit analysis result '" + resultUuid + "' does not exist");
        }
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

    @Transactional(readOnly = true)
    public FaultResult getOneBusFaultResult(UUID resultUuid, String stringFilters, Sort sort) {
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<ShortCircuitAnalysisResultEntity> resultEntity = resultService.find(resultUuid);
        if (resultEntity.isEmpty()) {
            throw new ComputationException(RESULT_NOT_FOUND, "The short circuit analysis result '" + resultUuid + "' does not exist");
        }
        Page<FeederResultEntity> feederResultEntitiesPage = resultService.findFeederResultsPage(resultEntity.get(), resourceFilters, Pageable.unpaged(sort));
        if (feederResultEntitiesPage.isEmpty()) {
            ShortCircuitAnalysisResult result = fromEntity(resultEntity.get(), FaultResultsMode.FULL);
            return result.getFaults().getFirst();
        }
        FaultResult faultResult = buildFaultResultFromSomeOfItsFeederResultEntities(feederResultEntitiesPage.getContent());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(GET_SHORT_CIRCUIT_RESULTS_MSG, resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        }
        return faultResult;
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

}
