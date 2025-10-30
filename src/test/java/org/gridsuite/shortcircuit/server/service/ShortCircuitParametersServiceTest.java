/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShortCircuitParametersServiceTest implements WithAssertions {

    private ShortCircuitParametersService parametersService;
    private ParametersRepository parametersRepository;

    @BeforeAll
    void setup() {
        this.parametersRepository = mock(ParametersRepository.class);
        this.parametersService = new ShortCircuitParametersService(parametersRepository);
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
        verify(pEntity).getPredefinedParameters();
        verify(pEntity).getSpecificParameters();
        verify(pEntity).toShortCircuitParameters();
    }

    private void checkParametersEntityHasBeenUpdate(final ShortCircuitParametersEntity pEntity,
                                                    final ShortCircuitParametersEntity pEntityUpdate,
                                                    final String pEntityUpdateDesc,
                                                    ShortCircuitParametersInfos pDtoUpdate) {
        final ArgumentCaptor<ShortCircuitParametersInfos> infosCaptor = ArgumentCaptor.forClass(ShortCircuitParametersInfos.class);
        verify(pEntity).update(infosCaptor.capture());
        assertThat(infosCaptor.getValue()).as(pEntityUpdateDesc)
            .usingRecursiveComparison().ignoringFields("id").isEqualTo(pDtoUpdate);

        // verify possible updates (to pass verifyNoMoreInteractions)
        // verify(pEntity).setWithLimitViolations(pEntityUpdate.isWithLimitViolations());
        // verify(pEntity).setWithVoltageResult(pEntityUpdate.isWithVoltageResult());
        // verify(pEntity).setWithFeederResult(pEntityUpdate.isWithFeederResult());
        // verify(pEntity).setStudyType(pEntityUpdate.getStudyType());
        // verify(pEntity).setMinVoltageDropProportionalThreshold(pEntityUpdate.getMinVoltageDropProportionalThreshold());
        // verify(pEntity).setPredefinedParameters(pEntityUpdate.getPredefinedParameters());
        // verify(pEntity).setWithLoads(pEntityUpdate.isWithLoads());
        // verify(pEntity).setWithShuntCompensators(pEntityUpdate.isWithShuntCompensators());
        // verify(pEntity).setWithVscConverterStations(pEntityUpdate.isWithVscConverterStations());
        // verify(pEntity).setWithNeutralPosition(pEntityUpdate.isWithNeutralPosition());
        // verify(pEntity).setInitialVoltageProfileMode(pEntityUpdate.getInitialVoltageProfileMode());
    }

    // private void checkParametersEntityHasBeenUpdate(final ShortCircuitParametersEntity pEntity,
    //                                                 final ShortCircuitParametersEntity pEntityUpdate, final String pEntityUpdateDesc, ShortCircuitParametersInfos pDtoUpdate) {
    //     assertThat(pEntity).as(pEntityUpdateDesc)
    //         .usingRecursiveComparison().isEqualTo(pEntityUpdate);

    //     // verify possible updates (to pass verifyNoMoreInteractions)
    //     // verify(pEntity).update(pDtoUpdate != null ? pDtoUpdate : parametersService.getDefaultParametersInfos());
    //     // verify(pEntity, times(4)).isWithLimitViolations();
    //     // verify(pEntity, times(4)).isWithVoltageResult();
    //     // verify(pEntity, times(4)).isWithFeederResult();
    //     // verify(pEntity, times(4)).getStudyType();
    //     // verify(pEntity, times(4)).getMinVoltageDropProportionalThreshold();
    //     // verify(pEntity, times(4)).getPredefinedParameters();
    //     // verify(pEntity, times(4)).isWithLoads();
    //     // verify(pEntity, times(4)).isWithShuntCompensators();
    //     // verify(pEntity, times(4)).isWithVscConverterStations();
    //     // verify(pEntity, times(4)).isWithNeutralPosition();
    //     // verify(pEntity, times(4)).getInitialVoltageProfileMode();

    //     // verify possible updates (to pass verifyNoMoreInteractions)
    //     verify(pEntity).setWithLimitViolations(pEntityUpdate.isWithLimitViolations());
    //     verify(pEntity).setWithVoltageResult(pEntityUpdate.isWithVoltageResult());
    //     verify(pEntity).setWithFeederResult(pEntityUpdate.isWithFeederResult());
    //     verify(pEntity).setStudyType(pEntityUpdate.getStudyType());
    //     verify(pEntity).setMinVoltageDropProportionalThreshold(pEntityUpdate.getMinVoltageDropProportionalThreshold());
    //     verify(pEntity).setPredefinedParameters(pEntityUpdate.getPredefinedParameters());
    //     verify(pEntity).setWithLoads(pEntityUpdate.isWithLoads());
    //     verify(pEntity).setWithShuntCompensators(pEntityUpdate.isWithShuntCompensators());
    //     verify(pEntity).setWithVscConverterStations(pEntityUpdate.isWithVscConverterStations());
    //     verify(pEntity).setWithNeutralPosition(pEntityUpdate.isWithNeutralPosition());
    //     verify(pEntity).setInitialVoltageProfileMode(pEntityUpdate.getInitialVoltageProfileMode());
    // }

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
        final ShortCircuitParametersEntity pEntity = spy(new ShortCircuitParametersEntity(pUuid, false, false, false, StudyType.STEADY_STATE, minVoltDrop,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, false, false, false, false, InitialVoltageProfileMode.NOMINAL, Collections.emptyList()));
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        //can't spy call to fromEntity()
        assertThat(parametersService.getParameters(pUuid)).as("service call result")
                .get().as("dto").usingRecursiveComparison().isEqualTo(new ShortCircuitParametersInfos(
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
        assertThat(parametersService.createParameters(new ShortCircuitParametersInfos(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters(), null)))
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
        final ShortCircuitParametersInfos pDtoUpdateInfos = spy(new ShortCircuitParametersInfos(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, pDtoUpdateParams, Collections.emptyMap()));
        //dto that must have differences with defaults
        final ShortCircuitParametersEntity pEntityUpdate = new ShortCircuitParametersEntity(pUuid, true, true, true, StudyType.TRANSIENT, 0.0,
            ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, true, true, true, false, InitialVoltageProfileMode.NOMINAL, Collections.emptyList());
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));

        parametersService.updateParameters(pUuid, pDtoUpdateInfos);

        // the service will save the updated entity back to the repository
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);
        // verify DTO has been read
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
        checkParametersEntityHasBeenUpdate(pEntity, pEntityUpdate, "entity from dto", pDtoUpdateInfos);
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity);
    }

    @Test
    void testResetExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersInfos defaultInfos = parametersService.getDefaultParametersInfos();
        final ShortCircuitParametersEntity pEntity = spy(new ShortCircuitParametersEntity(defaultInfos).setId(pUuid));
        //entity that must have differences with defaults
        final ShortCircuitParametersEntity pModifiedEntity = new ShortCircuitParametersEntity(pUuid, true, false, false, StudyType.TRANSIENT, 20.0,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP, false, false, true, true, InitialVoltageProfileMode.NOMINAL, null);
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        parametersService.updateParameters(pUuid, null);
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);

        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pModifiedEntity);
        // verify the parameters has been update
        checkParametersEntityHasBeenUpdate(pEntity, pModifiedEntity, "default entity", defaultInfos);
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity);
    }
}
