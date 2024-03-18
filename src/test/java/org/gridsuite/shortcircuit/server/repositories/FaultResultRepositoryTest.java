/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.BusFault;
import com.powsybl.shortcircuit.FaultResult;
import com.powsybl.shortcircuit.FeederResult;
import com.powsybl.shortcircuit.FortescueFaultResult;
import com.powsybl.shortcircuit.FortescueValue;
import com.powsybl.shortcircuit.MagnitudeFaultResult;
import com.powsybl.shortcircuit.MagnitudeFeederResult;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import org.gridsuite.shortcircuit.server.dto.FaultResultsMode;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.shortcircuit.server.TestUtils.MOCK_RUN_CONTEXT;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@SpringBootTest // would be better with @DataJpaTest but does not work here
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // improve tests speed as we only read DB
class FaultResultRepositoryTest {

    static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("CONN_ID_1", 22.17);
    static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("CONN_ID_2", 18.57);
    static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("CONN_ID_3", 53.94);
    static final FeederResult FEEDER_RESULT_4 = new MagnitudeFeederResult("RARDELNO15", 37.15);
    static final LimitViolation LIMIT_VIOLATION_1 = new LimitViolation("SUBJECT_1", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 25.63, 4f, 33.54);
    static final LimitViolation LIMIT_VIOLATION_2 = new LimitViolation("SUBJECT_2", LimitViolationType.LOW_SHORT_CIRCUIT_CURRENT, 12.17, 2f, 10.56);
    static final LimitViolation LIMIT_VIOLATION_3 = new LimitViolation("SUBJECT_3", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 45.12, 5f, 54.3);
    static final FaultResult FAULT_RESULT_1 = new MagnitudeFaultResult(new BusFault("A_VLHV1_0", "ELEMENT_ID_1"), 17.0,
            List.of(FEEDER_RESULT_1, FEEDER_RESULT_3),
            List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2),
            45.3, FaultResult.Status.SUCCESS);
    static final FaultResult FAULT_RESULT_2 = new MagnitudeFaultResult(new BusFault("B_VLHV2_0", "ELEMENT_ID_2"), 18.0,
            List.of(FEEDER_RESULT_2, FEEDER_RESULT_1, FEEDER_RESULT_4, FEEDER_RESULT_3),
            List.of(LIMIT_VIOLATION_2),
        47.3, FaultResult.Status.SUCCESS);
    static final FaultResult FAULT_RESULT_3 = new MagnitudeFaultResult(new BusFault("C_VLGEN_0", "ELEMENT_ID_3"), 19.0,
            List.of(), List.of(LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
        49.3, FaultResult.Status.SUCCESS);
    static final FaultResult FAULT_RESULT_4 = new FortescueFaultResult(new BusFault("A_VLHV2_0", "ELEMENT_ID_2"), 18.0,
        List.of(), List.of(),
        new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), Collections.emptyList(), null, FaultResult.Status.SUCCESS);
    static final ShortCircuitAnalysisResult RESULT_MAGNITUDE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1, FAULT_RESULT_2, FAULT_RESULT_3));
    static final ShortCircuitAnalysisResult RESULT_FORTESCUE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_4));
    static final UUID MAGNITUDE_RESULT_UUID = UUID.randomUUID();
    static final UUID FORTESCUE_RESULT_UUID = UUID.randomUUID();

    private FaultResultEntity faultResultEntity1;
    private FaultResultEntity faultResultEntity2;
    private FaultResultEntity faultResultEntity3;
    private FaultResultEntity faultResultEntity4;
    private ShortCircuitAnalysisResultEntity resultMagnitudeEntity;
    private ShortCircuitAnalysisResultEntity resultFortescueEntity;

    @Autowired
    private ShortCircuitAnalysisResultRepository shortCircuitAnalysisResultRepository;

    @BeforeAll
    void setUp() {
        // Magnitude faults
        shortCircuitAnalysisResultRepository.insert(MAGNITUDE_RESULT_UUID, RESULT_MAGNITUDE_FULL, MOCK_RUN_CONTEXT, "");
        resultMagnitudeEntity = shortCircuitAnalysisResultRepository.findFullResults(MAGNITUDE_RESULT_UUID).get();
        List<FaultResultEntity> faultResultEntities = resultMagnitudeEntity.getFaultResults().stream()
            .sorted(Comparator.comparing(faultResultEntity -> faultResultEntity.getFault().getId()))
            .toList();
        faultResultEntity1 = faultResultEntities.get(0);
        faultResultEntity2 = faultResultEntities.get(1);
        faultResultEntity3 = faultResultEntities.get(2);
        // Fortescue fault
        shortCircuitAnalysisResultRepository.insert(FORTESCUE_RESULT_UUID, RESULT_FORTESCUE_FULL, MOCK_RUN_CONTEXT, "");
        resultFortescueEntity = shortCircuitAnalysisResultRepository.findFullResults(FORTESCUE_RESULT_UUID).get();
        faultResultEntity4 = resultFortescueEntity.getFaultResults().stream().findFirst().get();
    }

    @AfterAll
    void tearDown() {
        shortCircuitAnalysisResultRepository.deleteAll();
    }

    @ParameterizedTest(name = "[{index}] Using the filter(s) {1} should return the given entities")
    @MethodSource({
        "provideOrEqualsNestedFieldsFilters",
        "provideContainsNestedFieldsFilters",
        "provideNotEqualFilters",
        "provideNotEqualNestedFieldsFilters"
    })
    void faultResultFilterTest(ShortCircuitAnalysisResultEntity resultEntity, List<ResourceFilter> resourceFilters, List<FaultResultEntity> faultList) {
        Page<FaultResultEntity> faultPage = shortCircuitAnalysisResultRepository.findFaultResultsPage(resultEntity, resourceFilters, Pageable.unpaged(), FaultResultsMode.BASIC);
        assertThat(faultPage.getContent()).extracting("fault.id").describedAs("Check if the IDs of the fault page are correct")
            .containsExactlyInAnyOrderElementsOf(faultList.stream().map(faultResultEntity -> faultResultEntity.getFault().getId()).toList());
    }

    @ParameterizedTest(name = "[{index}] Using the filter(s) {1} should return the given entities")
    @MethodSource({
        "provideOrEqualsNestedFieldsFilters",
        "provideContainsNestedFieldsFilters",
        "provideNotEqualFilters",
        "provideNotEqualNestedFieldsFilters"
    })
    void faultResultFilterWithPageableTest(ShortCircuitAnalysisResultEntity resultEntity, List<ResourceFilter> resourceFilters) {
        //Test with unsorted request and expect the result to be sorted by uuid anyway
        Page<FaultResultEntity> faultPage = shortCircuitAnalysisResultRepository.findFaultResultsPage(resultEntity, resourceFilters, Pageable.ofSize(3).withPage(0), FaultResultsMode.BASIC);
        assertFaultEqualsInOrder(faultPage, Comparator.comparing(o -> o.getFaultResultUuid().toString()));

        //Test with pageable containing a sort by current and expect the results to be sorted by current
        faultPage = shortCircuitAnalysisResultRepository.findFaultResultsPage(resultEntity, resourceFilters, PageRequest.of(0, 3, Sort.by(new Sort.Order(Sort.Direction.ASC, "current"))), FaultResultsMode.BASIC);
        assertFaultEqualsInOrder(faultPage, Comparator.comparing(FaultResultEntity::getCurrent));

        //Test with pageable containing a sort by nbLimitViolations and since some values are equals we except the result to be sorted by nbLimitViolations first and then by uuid
        faultPage = shortCircuitAnalysisResultRepository.findFaultResultsPage(resultEntity, resourceFilters, PageRequest.of(0, 3, Sort.by(new Sort.Order(Sort.Direction.ASC, "nbLimitViolations"))), FaultResultsMode.BASIC);
        assertFaultEqualsInOrder(faultPage, Comparator.comparing(FaultResultEntity::getNbLimitViolations).thenComparing(o -> o.getFaultResultUuid().toString()));
    }

    @ParameterizedTest(name = "[{index}] Using the filter(s) {1} and sort {2} should return the given entities")
    @MethodSource({
        "provideFeederFieldsStartFilters",
        "provideFeederFieldsSorted",
        "provideFeederFieldsContainFiltersAndSorted",
        "provideFeederFieldsUnfilteredSortedAscAndDesc"
    })
    void feedersFilterAndSortTest(ShortCircuitAnalysisResultEntity resultEntity,
                           List<ResourceFilter> resourceFilters,
                           Sort sort,
                           List<List<FeederResult>> expectedFeedersLists) {
        Page<FaultResultEntity> faultPage = shortCircuitAnalysisResultRepository.findFaultResultsPage(
                resultEntity,
                resourceFilters,
                PageRequest.of(0, 3, sort),
                FaultResultsMode.FULL);

        List<List<String>> feedersConnectableIds = faultPage.getContent().stream()
                        .map(faultRes -> faultRes.getFeederResults().stream()
                                .map(FeederResultEntity::getConnectableId)
                                .toList()
                        ).toList();

        List<List<String>> expectedFeedersConnectableIds = expectedFeedersLists.stream()
                        .map(feederList -> feederList.stream()
                                .map(FeederResult::getConnectableId)
                                .toList()
                        ).toList();

        assertThat(feedersConnectableIds).isEqualTo(expectedFeedersConnectableIds);
    }

    private void assertFaultEqualsInOrder(Page<FaultResultEntity> resultFaultPage, Comparator<FaultResultEntity> comparator) {
        List<String> expectedFaultPageUuid = resultFaultPage.getContent().stream().sorted(comparator)
                .map(faultResultEntity -> faultResultEntity.getFault().getId()).toList();
        assertThat(resultFaultPage.getContent()).extracting("fault.id").describedAs("Check if the IDs of the fault page are correct")
                .containsExactlyElementsOf(expectedFaultPageUuid);
    }

    private Stream<Arguments> provideOrEqualsNestedFieldsFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, "THREE_PHASE", "fault.faultType")),
                List.of(faultResultEntity1, faultResultEntity2, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, "HIGH_SHORT_CIRCUIT_CURRENT", "limitViolations.limitType")),
                List.of(faultResultEntity1, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, "HIGH_SHORT_CIRCUIT", "limitViolations.limitType")),
                List.of()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, List.of("HIGH_SHORT_CIRCUIT_CURRENT", "HIGH_SHORT_CIRCUIT"), "limitViolations.limitType")),
                List.of(faultResultEntity1, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, "LOW_SHORT_CIRCUIT_CURRENT", "limitViolations.limitType")),
                List.of(faultResultEntity1, faultResultEntity2, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.EQUALS, null, "limitViolations.limitType")),
                List.of()));
    }

    private Stream<Arguments> provideFeederFieldsStartFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "C", "feederResults.connectableId")),
                Sort.by(new Sort.Order(Sort.Direction.ASC, "current")),
                List.of(List.of(FEEDER_RESULT_3, FEEDER_RESULT_1), List.of(FEEDER_RESULT_3, FEEDER_RESULT_1, FEEDER_RESULT_2))),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "RAR", "feederResults.connectableId")),
                Sort.by(new Sort.Order(Sort.Direction.ASC, "current")),
                List.of(List.of(FEEDER_RESULT_4))
            )
        );
    }

    private Stream<Arguments> provideFeederFieldsSorted() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(),
                Sort.by(
                    new Sort.Order(Sort.Direction.ASC, "current"),
                    new Sort.Order(Sort.Direction.ASC, "feederResults.connectableId")),
                List.of(List.of(FEEDER_RESULT_1, FEEDER_RESULT_3), List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3, FEEDER_RESULT_4), List.of())
            ),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(),
                Sort.by(
                    new Sort.Order(Sort.Direction.ASC, "current"),
                    new Sort.Order(Sort.Direction.DESC, "feederResults.connectableId")),
                List.of(
                        List.of(FEEDER_RESULT_3, FEEDER_RESULT_1),
                        List.of(FEEDER_RESULT_4, FEEDER_RESULT_3, FEEDER_RESULT_2, FEEDER_RESULT_1),
                        List.of()
                ))
        );
    }

    private Stream<Arguments> provideFeederFieldsContainFiltersAndSorted() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "1", "feederResults.connectableId")),
                    Sort.by(
                            new Sort.Order(Sort.Direction.ASC, "current"),
                            new Sort.Order(Sort.Direction.DESC, "feederResults.connectableId")),
                List.of(List.of(FEEDER_RESULT_1), List.of(FEEDER_RESULT_4, FEEDER_RESULT_1))),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "NN_I", "feederResults.connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "1", "feederResults.connectableId")),
                    Sort.by(
                            new Sort.Order(Sort.Direction.ASC, "current"),
                            new Sort.Order(Sort.Direction.DESC, "feederResults.connectableId")),
                List.of(
                    List.of(FEEDER_RESULT_1), List.of(FEEDER_RESULT_1)
                )),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                        new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "OUPS", "feederResults.connectableId")),
                Sort.by(
                        new Sort.Order(Sort.Direction.ASC, "current"),
                        new Sort.Order(Sort.Direction.DESC, "feederResults.connectableId")),
                List.of()
            )
        );
    }

    private Stream<Arguments> provideFeederFieldsUnfilteredSortedAscAndDesc() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(),
                    Sort.by(
                            new Sort.Order(Sort.Direction.ASC, "current"),
                            new Sort.Order(Sort.Direction.DESC, "feederResults.connectableId")),
                List.of(
                    List.of(FEEDER_RESULT_3, FEEDER_RESULT_1),
                    List.of(FEEDER_RESULT_4, FEEDER_RESULT_3, FEEDER_RESULT_2, FEEDER_RESULT_1),
                    List.of()
                )),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(),
                    Sort.by(
                            new Sort.Order(Sort.Direction.ASC, "current"),
                            new Sort.Order(Sort.Direction.ASC, "feederResults.connectableId")),
                List.of(
                    List.of(FEEDER_RESULT_1, FEEDER_RESULT_3),
                    List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3, FEEDER_RESULT_4),
                    List.of()
                )
            )
        );
    }

    private Stream<Arguments> provideContainsNestedFieldsFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "A_VLHV1_0", "fault.id")),
                List.of(faultResultEntity1)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "SUBJECT_1", "limitViolations.subjectId")),
                List.of(faultResultEntity1)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "SUBJECT_2", "limitViolations.subjectId")),
                List.of(faultResultEntity1, faultResultEntity2, faultResultEntity3))
        );
    }

    private Stream<Arguments> provideNotEqualFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 45.3, "current")),
                List.of(faultResultEntity2, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 47, "current")),
                List.of(faultResultEntity1, faultResultEntity2, faultResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 47.3, "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 49.3, "current")),
                List.of(faultResultEntity1)),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 2, "nbLimitViolations")),
                List.of(faultResultEntity2))
        );
    }

    private Stream<Arguments> provideNotEqualNestedFieldsFilters() {
        return Stream.of(
            //TODO FM need to fix it when we'll filter on limitViolations
//            Arguments.of(
//                resultMagnitudeEntity,
//                List.of(
//                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 33.54, "limitViolations.value")),
//                List.of(faultResultEntity2, faultResultEntity3)),
//            Arguments.of(
//                resultMagnitudeEntity,
//                List.of(
//                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 33.54, "limitViolations.value"),
//                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 54.3, "limitViolations.value")),
//                List.of(faultResultEntity2)),
//            Arguments.of(
//                resultMagnitudeEntity,
//                List.of(
//                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 10.56, "limitViolations.value")),
//                List.of()),
            Arguments.of(
                resultFortescueEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 42, "fortescueCurrent.positiveMagnitude")),
                List.of(faultResultEntity4)),
            Arguments.of(
                resultFortescueEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 21.328664779663086, "fortescueVoltage.positiveMagnitude")),
                List.of())
        );
    }

}
