/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitSpecificParameterEntity;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShortCircuitParametersServiceTest implements WithAssertions {

    private ShortCircuitParametersService parametersService;
    private ParametersRepository parametersRepository;

    @BeforeAll
    void setup() {
        this.parametersRepository = mock(ParametersRepository.class);
        this.parametersService = new ShortCircuitParametersService(parametersRepository, ShortCircuitParametersConstants.DEFAULT_PROVIDER);
    }

    @AfterEach
    void checkMocks() {
        try {
            Mockito.verifyNoMoreInteractions(
                parametersRepository
            );
        } finally {
            Mockito.reset(
                parametersRepository
            );
        }
    }

    private static void checkParametersEntityHasBeenRead(final ShortCircuitParametersEntity pEntity) {
        verify(pEntity).getProvider();
        verify(pEntity).getPredefinedParameters();
        verify(pEntity).getSpecificParameters();
        verify(pEntity).toShortCircuitParameters();
    }

    private void checkParametersEntityHasBeenUpdate(final ShortCircuitParametersEntity pEntity,
                                                    final String pEntityUpdateDesc,
                                                    ShortCircuitParametersInfos pDtoUpdate) {
        final ArgumentCaptor<ShortCircuitParametersInfos> infosCaptor = ArgumentCaptor.forClass(ShortCircuitParametersInfos.class);
        verify(pEntity).update(infosCaptor.capture());
        assertThat(infosCaptor.getValue()).as(pEntityUpdateDesc)
            .usingRecursiveComparison().ignoringFields("id").isEqualTo(pDtoUpdate);
    }

    @Test
    void testGetNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(parametersService.getParameters(pUuid)).as("service call result").isEmpty();
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testGetExistingParametersAndConversionToDto() {
        final UUID pUuid = UUID.randomUUID();
        final double minVoltDrop = new Random().nextDouble();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder()
            .id(pUuid)
            .withLimitViolations(false)
            .studyType(StudyType.STEADY_STATE)
            .minVoltageDropProportionalThreshold(minVoltDrop)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909)
            .withVscConverterStations(false)
            .withNeutralPosition(false)
            .build());
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        //can't spy call to fromEntity()
        assertThat(parametersService.getParameters(pUuid)).as("service call result")
                .get().as("dto").usingRecursiveComparison().isEqualTo(new ShortCircuitParametersInfos(
                        ShortCircuitParametersConstants.DEFAULT_PROVIDER,
                        ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters()
                            .setWithLimitViolations(false)
                            .setWithVoltageResult(false)
                            .setWithFeederResult(false)
                            .setStudyType(StudyType.STEADY_STATE)
                            .setMinVoltageDropProportionalThreshold(minVoltDrop)
                            .setWithLoads(false)
                            .setWithShuntCompensators(false)
                            .setWithVSCConverterStations(false)
                            .setWithNeutralPosition(false)
                            .setInitialVoltageProfileMode(InitialVoltageProfileMode.NOMINAL),
                        Collections.emptyMap()));
        checkParametersEntityHasBeenRead(pEntity);
        verifyNoMoreInteractions(pEntity);
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testDeleteNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.existsById(any(UUID.class))).thenReturn(false);
        assertThat(parametersService.deleteParameters(pUuid)).as("service call result").isFalse();
        verify(parametersRepository).existsById(pUuid);
    }

    @Test
    void testDeleteExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.existsById(any(UUID.class))).thenReturn(true);
        assertThat(parametersService.deleteParameters(pUuid)).as("service call result").isTrue();
        verify(parametersRepository).existsById(pUuid);
        verify(parametersRepository).deleteById(pUuid);
    }

    @Test
    void testDuplicateNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(parametersService.duplicateParameters(pUuid)).as("service call result").isEmpty();
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testDuplicateExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntityRaw = ShortCircuitParametersEntity.builder().id(UUID.randomUUID()).build();
        final ShortCircuitParametersEntity pEntity = spy(pEntityRaw);
        final UUID pSavedUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pSavedEntity = spy(ShortCircuitParametersEntity.builder().id(pSavedUuid).build());
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pSavedEntity);
        assertThat(parametersService.duplicateParameters(pUuid)).as("service call result").get().isEqualTo(pSavedUuid);
        verify(parametersRepository).findById(pUuid);
        checkParametersEntityHasBeenRead(pEntity);
        final ArgumentCaptor<ShortCircuitParametersEntity> entityCaptor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue()).as("saved entity").isNotSameAs(pEntity)
                .usingRecursiveComparison().ignoringFields("id").isEqualTo(pEntityRaw);
        verify(pSavedEntity).getId();
        verifyNoMoreInteractions(pEntity);
        verifyNoMoreInteractions(pSavedEntity);
    }

    @Test
    void testCreateParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);
        //dto that must have differences with defaults
        assertThat(parametersService.createParameters(new ShortCircuitParametersInfos(ShortCircuitParametersConstants.DEFAULT_PROVIDER, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters(), null)))
                .as("service call result").isEqualTo(pUuid);
        verify(pEntity).getId();
        verifyNoMoreInteractions(pEntity);
        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(ShortCircuitParametersEntity.builder()
            .withLimitViolations(true)
            .withVoltageResult(true)
            .withFeederResult(true)
            .studyType(StudyType.TRANSIENT)
            .minVoltageDropProportionalThreshold(0.0)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909)
            .withLoads(true)
            .withShuntCompensators(true)
            .withVscConverterStations(true)
            .withNeutralPosition(false)
            .build());
    }

    @Test
    void testCreateDefaultParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);
        assertThat(parametersService.createDefaultParameters()).as("service call result").isEqualTo(pUuid);
        verify(pEntity).getId();
        verifyNoMoreInteractions(pEntity);
        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(new ShortCircuitParametersEntity());
    }

    @Test
    void testUpdateNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> parametersService.updateParameters(pUuid, null))
            .isInstanceOf(NoSuchElementException.class);
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testUpdateExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        final ShortCircuitParameters pDtoUpdateParams = spy(new ShortCircuitParameters());
        //dto that must have differences with defaults
        final ShortCircuitParametersInfos pDtoUpdateInfos = spy(new ShortCircuitParametersInfos(ShortCircuitParametersConstants.DEFAULT_PROVIDER, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, pDtoUpdateParams, Collections.emptyMap()));
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));

        parametersService.updateParameters(pUuid, pDtoUpdateInfos);

        // the service will save the updated entity back to the repository
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);
        // verify DTO has been read
        verify(pDtoUpdateInfos).provider();
        verify(pDtoUpdateInfos).commonParameters();
        verify(pDtoUpdateInfos).predefinedParameters();
        verify(pDtoUpdateInfos).specificParametersPerProvider();
        verify(pDtoUpdateParams).isWithLimitViolations();
        verify(pDtoUpdateParams).isWithVoltageResult();
        verify(pDtoUpdateParams).isWithFeederResult();
        verify(pDtoUpdateParams).getStudyType();
        verify(pDtoUpdateParams).getMinVoltageDropProportionalThreshold();
        verify(pDtoUpdateParams).isWithLoads();
        verify(pDtoUpdateParams).isWithShuntCompensators();
        verify(pDtoUpdateParams).isWithVSCConverterStations();
        verify(pDtoUpdateParams).isWithNeutralPosition();
        verify(pDtoUpdateParams).getInitialVoltageProfileMode();
        verifyNoMoreInteractions(pDtoUpdateInfos, pDtoUpdateParams);
        // verify the parameters has been update
        checkParametersEntityHasBeenUpdate(pEntity, "entity from dto", pDtoUpdateInfos);
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity);
    }

    @Test
    void testResetExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersInfos defaultInfos = parametersService.getDefaultParametersInfos();
        final ShortCircuitParametersEntity entity = new ShortCircuitParametersEntity(defaultInfos);
        entity.setId(pUuid);
        final ShortCircuitParametersEntity pEntity = spy(entity);
        //entity that must have differences with defaults
        final ShortCircuitParametersEntity pModifiedEntity = ShortCircuitParametersEntity.builder()
            .id(pUuid)
            .withLimitViolations(false) // the diff
            .build();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        parametersService.updateParameters(pUuid, null);
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);

        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pModifiedEntity);
        // verify the parameters has been update
        checkParametersEntityHasBeenUpdate(pEntity, "default entity", defaultInfos);
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity);
    }

    // specific parameters
    @Test
    void testCreateParametersWithSpecificParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);

        final Map<String, Map<String, String>> specific = Collections.singletonMap("my_provider",
            Collections.singletonMap("param", "value"));

        final ShortCircuitParameters params = new ShortCircuitParameters(); // keep default common params
        final ShortCircuitParametersInfos infos = new ShortCircuitParametersInfos(
            ShortCircuitParametersConstants.DEFAULT_PROVIDER,
            ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP,
            params,
            specific
        );

        assertThat(parametersService.createParameters(infos)).as("service call result").isEqualTo(pUuid);

        verify(pEntity).getId();
        verifyNoMoreInteractions(pEntity);

        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        final ShortCircuitParametersEntity saved = captor.getValue();

        // ensure specific parameters have been converted and stored
        assertThat(saved.getSpecificParameters()).as("specific parameters list").hasSize(1);
        final ShortCircuitSpecificParameterEntity spec = saved.getSpecificParameters().get(0);
        assertThat(spec.getProvider()).as("provider key").isEqualTo("my_provider");
        assertThat(spec.getName()).as("parameter name").isEqualTo("param");
        assertThat(spec.getValue()).as("parameter value").isEqualTo("value");
    }

    @Test
    void testDuplicateParametersWithSpecificParameters() {
        final UUID sourceUuid = UUID.randomUUID();
        final UUID savedUuid = UUID.randomUUID();

        final ShortCircuitSpecificParameterEntity spec = new ShortCircuitSpecificParameterEntity(UUID.randomUUID(), "my_provider", "param", "value");
        final ShortCircuitParametersEntity pEntityRaw = ShortCircuitParametersEntity.builder()
            .specificParameters(List.of(spec))
            .build();
        final ShortCircuitParametersEntity pEntity = spy(pEntityRaw);
        final ShortCircuitParametersEntity pSavedEntity = spy(ShortCircuitParametersEntity.builder().id(savedUuid).build());

        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pSavedEntity);

        final Optional<UUID> result = parametersService.duplicateParameters(sourceUuid);
        assertThat(result).as("service call result").isPresent().contains(savedUuid);

        verify(parametersRepository).findById(sourceUuid);
        checkParametersEntityHasBeenRead(pEntity);

        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        final ShortCircuitParametersEntity savedArg = captor.getValue();

        assertThat(savedArg).as("saved entity must be a copy").isNotSameAs(pEntity);
        assertThat(savedArg.getSpecificParameters()).as("specific parameters copied").hasSize(1);
        final ShortCircuitSpecificParameterEntity savedSpec = savedArg.getSpecificParameters().get(0);
        assertThat(savedSpec.getProvider()).isEqualTo("my_provider");
        assertThat(savedSpec.getName()).isEqualTo("param");
        assertThat(savedSpec.getValue()).isEqualTo("value");

        verify(pSavedEntity).getId();
        verifyNoMoreInteractions(pEntity);
        verifyNoMoreInteractions(pSavedEntity);
    }

    @Test
    void testGetSpecificShortCircuitParametersNoProvider() {
        final String randomProvider = "non_existing_provider_" + UUID.randomUUID();
        final Map<String, List<Parameter>> result = ShortCircuitParametersService.getSpecificShortCircuitParameters(randomProvider);
        assertThat(result).as("no provider must match a random name").isEmpty();
    }

    @Test
    void testCreateAndRetrieveParametersWithSpecificParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);

        final Map<String, Map<String, String>> specific = Collections.singletonMap("my_provider",
            Collections.singletonMap("param", "value"));

        final ShortCircuitParametersInfos infos = new ShortCircuitParametersInfos(
            ShortCircuitParametersConstants.DEFAULT_PROVIDER,
            ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP,
            new ShortCircuitParameters(),
            specific
        );

        // create
        assertThat(parametersService.createParameters(infos)).as("service call result").isEqualTo(pUuid);

        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        final ShortCircuitParametersEntity saved = captor.getValue();

        // saved entity must contain converted specific parameters
        assertThat(saved.getSpecificParameters()).as("specific parameters list").hasSize(1);
        final ShortCircuitSpecificParameterEntity spec = saved.getSpecificParameters().get(0);
        assertThat(spec.getProvider()).as("provider key").isEqualTo("my_provider");
        assertThat(spec.getName()).as("parameter name").isEqualTo("param");
        assertThat(spec.getValue()).as("parameter value").isEqualTo("value");

        // now stub findById to return the saved entity and retrieve DTO
        when(parametersRepository.findById(pUuid)).thenReturn(Optional.of(saved));
        final ShortCircuitParametersInfos retrieved = parametersService.getParameters(pUuid).orElseThrow();
        assertThat(retrieved.specificParametersPerProvider()).as("retrieved specific parameters").isEqualTo(specific);

        verify(parametersRepository).findById(pUuid);
    }
}
