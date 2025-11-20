/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.shortcircuit.server.ShortCircuitException;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersValues;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitSpecificParameterEntity;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;

import lombok.NonNull;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com
 */
@Service
public class ShortCircuitParametersService {

    private final ParametersRepository parametersRepository;

    public ShortCircuitParametersService(@NonNull ParametersRepository shortCircuitParametersRepository) {
        this.parametersRepository = shortCircuitParametersRepository;
    }

    public ShortCircuitParametersInfos toShortCircuitParametersInfos(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return ShortCircuitParametersInfos.builder()
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
                .provider(ShortCircuitParametersConstants.DEFAULT_PROVIDER)
                .predefinedParameters(entity.getPredefinedParameters())
                .commonParameters(entity.toShortCircuitParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(ShortCircuitParametersConstants.DEFAULT_PROVIDER))
                        .collect(Collectors.toMap(ShortCircuitSpecificParameterEntity::getName,
                                ShortCircuitSpecificParameterEntity::getValue)))
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitParametersValues> getParametersValues(UUID parametersUuid, String provider) {
        // TODO replace when multiple providers support is added
        return parametersRepository.findById(parametersUuid).map(this::toShortCircuitParametersValues);
    }

    public ShortCircuitParametersValues getParametersValues(UUID parametersUuid) {
        return parametersRepository.findById(parametersUuid)
                .map(this::toShortCircuitParametersValues).orElseThrow(() -> new ShortCircuitException(ShortCircuitException.Type.PARAMETERS_NOT_FOUND,
                        "ShortCircuit parameters '" + parametersUuid + "' not found"));
    }

    @Transactional(readOnly = true)
    public Optional<ShortCircuitParametersInfos> getParameters(final UUID parametersUuid) {
        return parametersRepository.findById(parametersUuid).map(this::toShortCircuitParametersInfos);
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

    public UUID createDefaultParameters() {
        return parametersRepository.save(new ShortCircuitParametersEntity()).getId();
    }

    public ShortCircuitParametersInfos getDefaultParametersInfos() {
        return toShortCircuitParametersInfos(new ShortCircuitParametersEntity());
    }

    public ShortCircuitParametersValues getDefaultParametersValues(String provider) {
        return toShortCircuitParametersValues(provider, new ShortCircuitParametersEntity());
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, ShortCircuitParametersInfos parametersInfos) {
        ShortCircuitParametersEntity shortCircuitParametersEntity = parametersRepository.findById(parametersUuid).orElseThrow();
        //if the parameters is null it means it's a reset to defaultValues, but we need to keep the provider because it's updated separately
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
