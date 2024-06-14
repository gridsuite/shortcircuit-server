/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitConstants;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.computation.service.NotificationService;
import org.gridsuite.shortcircuit.server.computation.service.UuidGeneratorService;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitParametersEntity;
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

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShortCircuitServiceTest implements WithAssertions {
    private NotificationService notificationService;
    private UuidGeneratorService uuidGeneratorService;
    private ShortCircuitAnalysisResultService resultService;
    private ParametersRepository parametersRepository;
    private ObjectMapper objectMapper;
    private ShortCircuitService shortCircuitService;

    @BeforeAll
    void setUp() {
        this.notificationService = mock(NotificationService.class);
        this.uuidGeneratorService = spy(new UuidGeneratorService());
        this.resultService = mock(ShortCircuitAnalysisResultService.class);
        this.parametersRepository = mock(ParametersRepository.class);
        this.objectMapper = spy(new ObjectMapper());
        this.shortCircuitService = new ShortCircuitService(notificationService, uuidGeneratorService, resultService, parametersRepository, objectMapper);
    }

    @AfterEach
    void checkMocks() {
        try {
            Mockito.verifyNoMoreInteractions(
                notificationService,
                uuidGeneratorService,
                resultService,
                parametersRepository,
                objectMapper
            );
        } finally {
            Mockito.reset(
                notificationService,
                uuidGeneratorService,
                resultService,
                parametersRepository,
                objectMapper
            );
        }
    }

    private void checkParametersEntityHasBeenRead(final ShortCircuitParametersEntity pEntity) {
        verify(pEntity).isWithLimitViolations();
        verify(pEntity).isWithVoltageResult();
        verify(pEntity).isWithFortescueResult();
        verify(pEntity).isWithFeederResult();
        verify(pEntity).getStudyType();
        verify(pEntity).getMinVoltageDropProportionalThreshold();
        verify(pEntity).getPredefinedParameters();
        verify(pEntity).isWithLoads();
        verify(pEntity).isWithShuntCompensators();
        verify(pEntity).isWithVscConverterStations();
        verify(pEntity).isWithNeutralPosition();
        verify(pEntity, atLeastOnce()).getInitialVoltageProfileMode();
        verify(pEntity, atMost(2)).getInitialVoltageProfileMode(); // for fromEntity() case
    }

    @Test
    void testGetNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(shortCircuitService.getParameters(pUuid)).as("service call result").isEmpty();
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testGetExistingParametersAndConversionToDto() {
        final UUID pUuid = UUID.randomUUID();
        final double minVoltDrop = new Random().nextDouble();
        final ShortCircuitParametersEntity pEntity = spy(new ShortCircuitParametersEntity(pUuid, false, false, false, false, StudyType.STEADY_STATE, minVoltDrop,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, false, false, false, false, InitialVoltageProfileMode.NOMINAL));
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        //can't spy call to fromEntity()
        assertThat(shortCircuitService.getParameters(pUuid)).as("service call result")
                .get().as("dto").usingRecursiveComparison().isEqualTo(new ShortCircuitParametersInfos(
                        ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters()
                        .setWithLimitViolations(false)
                        .setWithVoltageResult(false)
                        .setWithFortescueResult(false)
                        .setWithFeederResult(false)
                        .setStudyType(StudyType.STEADY_STATE)
                        .setMinVoltageDropProportionalThreshold(minVoltDrop)
                        .setWithLoads(false)
                        .setWithShuntCompensators(false)
                        .setWithVSCConverterStations(false)
                        .setWithNeutralPosition(false)
                        .setInitialVoltageProfileMode(InitialVoltageProfileMode.NOMINAL)));
        checkParametersEntityHasBeenRead(pEntity);
        verifyNoMoreInteractions(pEntity);
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testDeleteNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.existsById(any(UUID.class))).thenReturn(false);
        assertThat(shortCircuitService.deleteParameters(pUuid)).as("service call result").isFalse();
        verify(parametersRepository).existsById(pUuid);
    }

    @Test
    void testDeleteExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.existsById(any(UUID.class))).thenReturn(true);
        assertThat(shortCircuitService.deleteParameters(pUuid)).as("service call result").isTrue();
        verify(parametersRepository).existsById(pUuid);
        verify(parametersRepository).deleteById(pUuid);
    }

    @Test
    void testDuplicateNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(shortCircuitService.duplicateParameters(pUuid)).as("service call result").isEmpty();
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
        assertThat(shortCircuitService.duplicateParameters(pUuid)).as("service call result").get().isEqualTo(pSavedUuid);
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
        assertThat(shortCircuitService.createParameters(new ShortCircuitParametersInfos(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters())))
                .as("service call result").isEqualTo(pUuid);
        verify(pEntity).getId();
        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(new ShortCircuitParametersEntity(
                ShortCircuitConstants.DEFAULT_WITH_LIMIT_VIOLATIONS,
                ShortCircuitConstants.DEFAULT_WITH_VOLTAGE_RESULT,
                ShortCircuitConstants.DEFAULT_WITH_FORTESCUE_RESULT,
                ShortCircuitConstants.DEFAULT_WITH_FEEDER_RESULT,
                ShortCircuitConstants.DEFAULT_STUDY_TYPE,
                ShortCircuitConstants.DEFAULT_MIN_VOLTAGE_DROP_PROPORTIONAL_THRESHOLD,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909,
                ShortCircuitConstants.DEFAULT_WITH_LOADS,
                ShortCircuitConstants.DEFAULT_WITH_SHUNT_COMPENSATORS,
                ShortCircuitConstants.DEFAULT_WITH_VSC_CONVERTER_STATIONS,
                ShortCircuitConstants.DEFAULT_WITH_NEUTRAL_POSITION,
                ShortCircuitConstants.DEFAULT_INITIAL_VOLTAGE_PROFILE_MODE
                //ShortCircuitConstants.DEFAULT_SUB_TRANSIENT_COEFFICIENT
                //ShortCircuitConstants.DEFAULT_DETAILED_REPORT
        ));
    }

    @Test
    void testCreateDefaultParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        when(parametersRepository.save(any(ShortCircuitParametersEntity.class))).thenReturn(pEntity);
        assertThat(shortCircuitService.createParameters(null)).as("service call result").isEqualTo(pUuid);
        verify(pEntity).getId();
        final ArgumentCaptor<ShortCircuitParametersEntity> captor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(parametersRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(new ShortCircuitParametersEntity());
    }

    @Test
    void testUpdateNonExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(shortCircuitService.updateOrResetParameters(pUuid, null)).as("service call result").isFalse();
        verify(parametersRepository).findById(pUuid);
    }

    @Test
    void testUpdateExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        final ShortCircuitParameters pDtoUpdateParams = spy(new ShortCircuitParameters());
        final ShortCircuitParametersInfos pDtoUpdate = spy(new ShortCircuitParametersInfos(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, pDtoUpdateParams));
        final ShortCircuitParametersEntity pEntityUpdate = new ShortCircuitParametersEntity(pUuid,
            ShortCircuitConstants.DEFAULT_WITH_LIMIT_VIOLATIONS,
            ShortCircuitConstants.DEFAULT_WITH_VOLTAGE_RESULT,
            ShortCircuitConstants.DEFAULT_WITH_FORTESCUE_RESULT,
            ShortCircuitConstants.DEFAULT_WITH_FEEDER_RESULT,
            ShortCircuitConstants.DEFAULT_STUDY_TYPE,
            ShortCircuitConstants.DEFAULT_MIN_VOLTAGE_DROP_PROPORTIONAL_THRESHOLD,
            ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909,
            ShortCircuitConstants.DEFAULT_WITH_LOADS,
            ShortCircuitConstants.DEFAULT_WITH_SHUNT_COMPENSATORS,
            ShortCircuitConstants.DEFAULT_WITH_VSC_CONVERTER_STATIONS,
            ShortCircuitConstants.DEFAULT_WITH_NEUTRAL_POSITION,
            ShortCircuitConstants.DEFAULT_INITIAL_VOLTAGE_PROFILE_MODE
            //ShortCircuitConstants.DEFAULT_SUB_TRANSIENT_COEFFICIENT
            //ShortCircuitConstants.DEFAULT_DETAILED_REPORT
        );
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        assertThat(shortCircuitService.updateOrResetParameters(pUuid, pDtoUpdate)).as("service call result").isTrue();
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);
        // verify DTO has been read
        verify(pDtoUpdate).parameters();
        verify(pDtoUpdate).predefinedParameters();
        verify(pDtoUpdateParams).isWithLimitViolations();
        verify(pDtoUpdateParams).isWithVoltageResult();
        verify(pDtoUpdateParams).isWithFortescueResult();
        verify(pDtoUpdateParams).isWithFeederResult();
        verify(pDtoUpdateParams).getStudyType();
        verify(pDtoUpdateParams).getMinVoltageDropProportionalThreshold();
        verify(pDtoUpdateParams).isWithLoads();
        verify(pDtoUpdateParams).isWithShuntCompensators();
        verify(pDtoUpdateParams).isWithVSCConverterStations();
        verify(pDtoUpdateParams).isWithNeutralPosition();
        verify(pDtoUpdateParams).getInitialVoltageProfileMode();
        // verify the parameters has been update
        checkParametersEntityHasBeenUpdate(pEntity, pEntityUpdate, "entity from dto");
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity, pDtoUpdate, pDtoUpdateParams);
    }

    @Test
    void testResetExistingParameters() {
        final UUID pUuid = UUID.randomUUID();
        final ShortCircuitParametersEntity pEntity = spy(ShortCircuitParametersEntity.builder().id(pUuid).build());
        final ShortCircuitParametersEntity pEntityUpdate = new ShortCircuitParametersEntity(pUuid, true, false, false, true, StudyType.TRANSIENT, 20.0,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP, false, false, true, true, InitialVoltageProfileMode.NOMINAL);
        when(parametersRepository.findById(any(UUID.class))).thenReturn(Optional.of(pEntity));
        assertThat(shortCircuitService.updateOrResetParameters(pUuid, null)).as("service call result").isTrue();
        // verify we search the correct uid
        verify(parametersRepository).findById(pUuid);
        // verify the parameters has been update
        checkParametersEntityHasBeenUpdate(pEntity, pEntityUpdate, "default entity");
        // verify no unwanted actions have been done
        verifyNoMoreInteractions(pEntity);
    }

    private void checkParametersEntityHasBeenUpdate(final ShortCircuitParametersEntity pEntity,
                                                    final ShortCircuitParametersEntity pEntityUpdate, final String pEntityUpdateDesc) {
        final ArgumentCaptor<ShortCircuitParametersEntity> entityCaptor = ArgumentCaptor.forClass(ShortCircuitParametersEntity.class);
        verify(pEntity).updateWith(entityCaptor.capture());
        assertThat(entityCaptor.getValue()).as(pEntityUpdateDesc)
            .usingRecursiveComparison().ignoringFields("id").isEqualTo(pEntityUpdate);

        // verify the entity has been at least compared with candidate update entity
        checkParametersEntityHasBeenRead(pEntity);

        // verify possible updates (to pass verifyNoMoreInteractions)
        verify(pEntity, atMostOnce()).setWithLimitViolations(pEntityUpdate.isWithLimitViolations());
        verify(pEntity, atMostOnce()).setWithVoltageResult(pEntityUpdate.isWithVoltageResult());
        verify(pEntity, atMostOnce()).setWithFortescueResult(pEntityUpdate.isWithFortescueResult());
        verify(pEntity, atMostOnce()).setWithFeederResult(pEntityUpdate.isWithFeederResult());
        verify(pEntity, atMostOnce()).setStudyType(pEntityUpdate.getStudyType());
        verify(pEntity, atMostOnce()).setMinVoltageDropProportionalThreshold(pEntityUpdate.getMinVoltageDropProportionalThreshold());
        verify(pEntity, atMostOnce()).setPredefinedParameters(pEntityUpdate.getPredefinedParameters());
        verify(pEntity, atMostOnce()).setWithLoads(pEntityUpdate.isWithLoads());
        verify(pEntity, atMostOnce()).setWithShuntCompensators(pEntityUpdate.isWithShuntCompensators());
        verify(pEntity, atMostOnce()).setWithVscConverterStations(pEntityUpdate.isWithVscConverterStations());
        verify(pEntity, atMostOnce()).setWithNeutralPosition(pEntityUpdate.isWithNeutralPosition());
        verify(pEntity, atMostOnce()).setInitialVoltageProfileMode(pEntityUpdate.getInitialVoltageProfileMode());
    }
}
