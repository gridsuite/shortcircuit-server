/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import org.gridsuite.computation.error.ComputationException;
import com.powsybl.iidm.network.ThreeSides;
import org.gridsuite.shortcircuit.server.dto.*;
import org.gridsuite.shortcircuit.server.entities.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.Double.NaN;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.powsybl.shortcircuit.Fault.*;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class ShortCircuitServiceTest {

    @MockitoSpyBean
    private ShortCircuitService shortCircuitService;

    @MockitoBean
    private ShortCircuitAnalysisResultService resultService;

    private ShortCircuitAnalysisResultEntity resultEntity;
    private FaultResultEntity faultResultEntity;
    private Fault fault;
    private ShortCircuitLimits limits;

    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID RESULT_UUID_NOT_FOUND = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");

    private static final String FAULT_ID = "faultId";
    private static final String FAULT_ELEMENT_ID = "faultElementId";
    private static final String FAULT_VOLTAGE_LEVEL_ID = "faultVoltageLevelId";
    private static final FaultType FAULT_TYPE = FaultType.SINGLE_PHASE;

    private static final String CONNECTABLE_ID = "connectableId";
    private static final ThreeSides SIDE = ThreeSides.ONE;

    @BeforeAll
    void setUp() {
        FaultEmbeddable faultEmbeddable = new FaultEmbeddable(
                FAULT_ID,
                FAULT_ELEMENT_ID,
                FAULT_VOLTAGE_LEVEL_ID,
                FAULT_TYPE
        );
        faultResultEntity = new FaultResultEntity();
        faultResultEntity.setFault(faultEmbeddable);
        faultResultEntity.setCurrent(50);
        faultResultEntity.setShortCircuitPower(20);
        faultResultEntity.setIpMin(10.5);
        faultResultEntity.setIpMax(200);
        faultResultEntity.setDeltaCurrentIpMin(34.8);
        faultResultEntity.setDeltaCurrentIpMax(-154.7);
        faultResultEntity.setLimitViolations(List.of());
        faultResultEntity.setFeederResults(List.of());

        resultEntity = new ShortCircuitAnalysisResultEntity();
        resultEntity.setFaultResults(Set.of(faultResultEntity));

        fault = new Fault(
                FAULT_ID,
                FAULT_ELEMENT_ID,
                FAULT_VOLTAGE_LEVEL_ID,
                FAULT_TYPE.name()
        );

        limits = new ShortCircuitLimits(10.5, 200, 34.8, -154.7);
    }

    @Test
    void getOneBusFaultResultsWhenResultNotFoundTest() {
        when(resultService.find(RESULT_UUID_NOT_FOUND)).thenReturn(Optional.empty());
        ComputationException exception = assertThrows(
                ComputationException.class,
                () -> shortCircuitService.getOneBusFaultResult(RESULT_UUID_NOT_FOUND, null, Sort.unsorted())
        );
        assertEquals(RESULT_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(RESULT_UUID_NOT_FOUND.toString()));
    }

    @Test
    void getOneBusFaultResultsWhenFeederResultIsEmptyTest() {
        when(resultService.find(RESULT_UUID)).thenReturn(Optional.of(resultEntity));

        Page<FeederResultEntity> emptyPage = Page.empty();
        when(resultService.findFeederResultsPage(resultEntity, List.of(), Pageable.unpaged(Sort.unsorted()))).thenReturn(emptyPage);

        FaultResult faultResult = new FaultResult(fault, 50, NaN, 20, List.of(), List.of(), limits);
        FaultResult actualFaultResult = shortCircuitService.getOneBusFaultResult(RESULT_UUID, null, Sort.unsorted());
        assertThat(actualFaultResult)
                .usingRecursiveComparison()
                .withComparatorForType(
                        (d1, d2) -> Double.isNaN(d1) && Double.isNaN(d2) ? 0 : Double.compare(d1, d2),
                        Double.class
                ).isEqualTo(faultResult);
    }

    @Test
    void getOneBusFaultResultsWhenFeederResultIsNotEmptyTest() {
        when(resultService.find(RESULT_UUID)).thenReturn(Optional.of(resultEntity));

        FortescueResultEmbeddable fre = new FortescueResultEmbeddable();
        fre.setPositiveMagnitude(NaN);
        FeederResultEntity feederResultEntity = new FeederResultEntity(CONNECTABLE_ID, 50, fre, SIDE);
        feederResultEntity.setFaultResult(faultResultEntity);
        Page<FeederResultEntity> emptyPage = new PageImpl<>(List.of(feederResultEntity), Pageable.unpaged(), 1);
        when(resultService.findFeederResultsPage(resultEntity, List.of(), Pageable.unpaged(Sort.unsorted()))).thenReturn(emptyPage);

        FeederResult feederResult = new FeederResult(CONNECTABLE_ID, 50, NaN, SIDE.name());
        FaultResult faultResultWithFeederResults = new FaultResult(fault, 50, NaN, 20, List.of(), List.of(feederResult), limits);
        FaultResult actualFaultResult = shortCircuitService.getOneBusFaultResult(RESULT_UUID, null, Sort.unsorted());
        assertThat(actualFaultResult)
                .usingRecursiveComparison()
                .withComparatorForType(
                        (d1, d2) -> Double.isNaN(d1) && Double.isNaN(d2) ? 0 : Double.compare(d1, d2),
                        Double.class
                ).isEqualTo(faultResultWithFeederResults);
    }
}
