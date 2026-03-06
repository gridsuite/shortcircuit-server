/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.shortcircuit.server.dto.FilterElements;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersValues;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitSpecificParameterEntity;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.computation.error.ComputationBusinessErrorCode.PARAMETERS_NOT_FOUND;
import static org.gridsuite.shortcircuit.server.service.ShortCircuitService.NODE_CLUSTER_FILTER_IDS;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Service
public class ShortCircuitParametersService {

    private final ParametersRepository parametersRepository;

    @Getter
    private final String defaultProvider;
    private final FilterService filterService;

    public ShortCircuitParametersService(@NonNull ParametersRepository shortCircuitParametersRepository,
                                         @Value("${shortcircuit-analysis.default-provider}") String defaultProvider, FilterService filterService) {
        this.parametersRepository = shortCircuitParametersRepository;
        this.defaultProvider = defaultProvider;
        this.filterService = filterService;
    }

    public ShortCircuitParametersInfos toShortCircuitParametersInfos(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return ShortCircuitParametersInfos.builder()
            .provider(entity.getProvider())
            .predefinedParameters(entity.getPredefinedParameters())
            .commonParameters(entity.toShortCircuitParameters())
            .specificParametersPerProvider(entity.getSpecificParameters().stream()
                .collect(Collectors.groupingBy(ShortCircuitSpecificParameterEntity::getProvider,
                    Collectors.toMap(ShortCircuitSpecificParameterEntity::getName,
                        ShortCircuitSpecificParameterEntity::getValue))))
            .build();
    }

    public ShortCircuitParametersValues toShortCircuitParametersValues(String provider, ShortCircuitParametersEntity entity) {
        return ShortCircuitParametersValues.builder()
                .provider(provider)
                .predefinedParameters(entity.getPredefinedParameters())
                .commonParameters(entity.toShortCircuitParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(provider))
                        .collect(Collectors.toMap(ShortCircuitSpecificParameterEntity::getName,
                                ShortCircuitSpecificParameterEntity::getValue)))
                .build();
    }

    public ShortCircuitParametersValues toShortCircuitParametersValues(ShortCircuitParametersEntity entity) {
        return ShortCircuitParametersValues.builder()
                .provider(entity.getProvider())
                .predefinedParameters(entity.getPredefinedParameters())
                .commonParameters(entity.toShortCircuitParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(entity.getProvider()))
                        .collect(Collectors.toMap(ShortCircuitSpecificParameterEntity::getName,
                                ShortCircuitSpecificParameterEntity::getValue)))
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitParametersValues> getParametersValues(UUID parametersUuid, String provider) {
        return parametersRepository.findById(parametersUuid).map(entity -> toShortCircuitParametersValues(provider, entity));
    }

    public ShortCircuitParametersValues getParametersValues(UUID parametersUuid) {
        return parametersRepository.findById(parametersUuid)
                .map(this::toShortCircuitParametersValues).orElseThrow(() -> new ComputationException(PARAMETERS_NOT_FOUND,
                        "ShortCircuit parameters '" + parametersUuid + "' not found"));
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitParametersInfos> getParameters(final UUID parametersUuid) {
        return parametersRepository.findById(parametersUuid)
                .map(this::checkFilterExistenceForNodeCluster)
                .map(this::toShortCircuitParametersInfos);
    }

    private ShortCircuitParametersEntity checkFilterExistenceForNodeCluster(ShortCircuitParametersEntity shortCircuitParametersEntity) {
        ShortCircuitSpecificParameterEntity nodeClusterFilterIdsEntity = getNodeClusterFilterSpecificParameter(shortCircuitParametersEntity);
        if (nodeClusterFilterIdsEntity != null) {
            shortCircuitParametersEntity.getSpecificParameters().remove(nodeClusterFilterIdsEntity);
            Map<UUID, FilterElements> localFilters = deserializeFilterFromNodeCluster(nodeClusterFilterIdsEntity);
            List<UUID> filterMetadata = filterService.getFilters(localFilters.keySet().stream().toList()).stream().map(AbstractFilter::getId).toList();
            localFilters.forEach((filterId, filterElement) -> {
                if (!filterMetadata.contains(filterId)) {
                    filterElement.setFilterName(null);
                }
            });
            reAddNodeClusterToSpecificParameters(shortCircuitParametersEntity, nodeClusterFilterIdsEntity, localFilters);
        }
        return shortCircuitParametersEntity;
    }

    private void reAddNodeClusterToSpecificParameters(ShortCircuitParametersEntity shortCircuitParametersEntity, ShortCircuitSpecificParameterEntity nodeClusterFilterIdsEntity, Map<UUID, FilterElements> localFilters) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            shortCircuitParametersEntity.getSpecificParameters().add(new ShortCircuitSpecificParameterEntity(nodeClusterFilterIdsEntity.getId(),
                    nodeClusterFilterIdsEntity.getProvider(), NODE_CLUSTER_FILTER_IDS, objectMapper.writeValueAsString(localFilters.values())));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<UUID, FilterElements> deserializeFilterFromNodeCluster(ShortCircuitSpecificParameterEntity nodeClusterFilterIdsEntity) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<UUID, FilterElements> localFilters;
        try {
            localFilters = objectMapper.readValue(nodeClusterFilterIdsEntity.getValue(), new TypeReference<List<FilterElements>>() { }).stream()
                    .collect(Collectors.toMap(FilterElements::getFilterId, Function.identity()));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return localFilters;
    }

    private ShortCircuitSpecificParameterEntity getNodeClusterFilterSpecificParameter(ShortCircuitParametersEntity shortCircuitParametersEntity) {
        ShortCircuitSpecificParameterEntity nodeClusterFilterIdsEntity = null;
        for (ShortCircuitSpecificParameterEntity shortCircuitSpecificParameterEntity : shortCircuitParametersEntity
                .getSpecificParameters()) {
            if (shortCircuitSpecificParameterEntity.getName().equals(NODE_CLUSTER_FILTER_IDS)) {
                nodeClusterFilterIdsEntity = shortCircuitSpecificParameterEntity;
                break;
            }
        }
        return nodeClusterFilterIdsEntity;
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
                                   .map(e -> toShortCircuitParametersInfos(e).toEntity())
                                   .map(parametersRepository::save)
                                   .map(ShortCircuitParametersEntity::getId);
    }

    public UUID createParameters(ShortCircuitParametersInfos parameters) {
        return parametersRepository.save(parameters.toEntity()).getId();
    }

    private ShortCircuitParametersEntity getDefaultEntity() {
        return ShortCircuitParametersEntity.builder().provider(getDefaultProvider()).build();
    }

    public UUID createDefaultParameters() {
        return parametersRepository.save(getDefaultEntity()).getId();
    }

    public ShortCircuitParametersInfos getDefaultParametersInfos() {
        return toShortCircuitParametersInfos(getDefaultEntity());
    }

    public ShortCircuitParametersValues getDefaultParametersValues() {
        return toShortCircuitParametersValues(getDefaultEntity());
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, ShortCircuitParametersInfos parametersInfos) {
        ShortCircuitParametersEntity shortCircuitParametersEntity = parametersRepository.findById(parametersUuid).orElseThrow();
        //if the parameters is null it means it's a reset to defaultValues
        if (parametersInfos == null) {
            shortCircuitParametersEntity.update(getDefaultParametersInfos());
        } else {
            shortCircuitParametersEntity.update(parametersInfos);
        }
    }

    public static Map<String, List<Parameter>> getSpecificShortCircuitParameters(String providerName) {
        return ShortCircuitAnalysisProvider.findAll().stream()
                .filter(provider -> providerName == null || provider.getName().equals(providerName))
                .map(provider -> {
                    List<Parameter> params = provider.getSpecificParameters(PlatformConfig.defaultConfig()).stream()
                            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
                            .toList();
                    return Pair.of(provider.getName(), params);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }
}
