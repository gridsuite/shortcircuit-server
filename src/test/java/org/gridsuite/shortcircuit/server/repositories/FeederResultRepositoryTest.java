/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.BusFault;
import com.powsybl.shortcircuit.FaultResult;
import com.powsybl.shortcircuit.FeederResult;
import com.powsybl.shortcircuit.FortescueFaultResult;
import com.powsybl.shortcircuit.FortescueFeederResult;
import com.powsybl.shortcircuit.FortescueValue;
import com.powsybl.shortcircuit.MagnitudeFaultResult;
import com.powsybl.shortcircuit.MagnitudeFeederResult;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.shortcircuit.server.TestUtils.MOCK_RUN_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
@SpringBootTest // would be better with @DataJpaTest but does not work here
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // improve tests speed as we only read DB
class FeederResultRepositoryTest {

    static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("A_CONN_ID_1", 22.17);
    static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("A_CONN_ID_2", 18.57);
    static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("B_CONN_ID_3", 53.94);
    static final FeederResult FEEDER_RESULT_4 = new FortescueFeederResult("A_CONN_ID_4", new FortescueValue(45.328664779663086, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    static final FeederResult FEEDER_RESULT_5 = new FortescueFeederResult("B_CONN_ID_5", new FortescueValue(52.568678656325887, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    static final FeederResult FEEDER_RESULT_6 = new FortescueFeederResult("B_CONN_ID_6", new FortescueValue(18.170874567665456, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN));

    static final FaultResult FAULT_RESULT_1 = new MagnitudeFaultResult(new BusFault("", ""), Double.NaN,
        List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(),
        Double.NaN, FaultResult.Status.SUCCESS);
    static final FaultResult FAULT_RESULT_2 = new FortescueFaultResult(new BusFault("", ""), Double.NaN,
        List.of(FEEDER_RESULT_4, FEEDER_RESULT_5, FEEDER_RESULT_6), List.of(),
        new FortescueValue(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN), new FortescueValue(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN), List.of(), null, FaultResult.Status.SUCCESS);

    static final ShortCircuitAnalysisResult RESULT_MAGNITUDE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_1));
    static final ShortCircuitAnalysisResult RESULT_FORTESCUE_FULL = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT_2));
    static final UUID MAGNITUDE_RESULT_UUID = UUID.randomUUID();
    static final UUID FORTESCUE_RESULT_UUID = UUID.randomUUID();

    private FeederResultEntity feederResultEntity1;
    private FeederResultEntity feederResultEntity2;
    private FeederResultEntity feederResultEntity3;
    private FeederResultEntity feederResultEntity4;
    private FeederResultEntity feederResultEntity5;
    private FeederResultEntity feederResultEntity6;

    private final ArrayList<FeederResultEntity> feederResultEntityMagnitudeList = new ArrayList<>();

    private final ArrayList<FeederResultEntity> feederResultEntityFortescueList = new ArrayList<>();
    private ShortCircuitAnalysisResultEntity resultMagnitudeEntity;
    private ShortCircuitAnalysisResultEntity resultFortescueEntity;

    @Autowired
    private ShortCircuitAnalysisResultRepository shortCircuitAnalysisResultRepository;

    @Autowired
    FeederResultRepository feederResultRepository;

    @BeforeAll
    void setUp() {
        // Magnitude fault
        shortCircuitAnalysisResultRepository.insert(MAGNITUDE_RESULT_UUID, RESULT_MAGNITUDE_FULL, MOCK_RUN_CONTEXT, "");
        resultMagnitudeEntity = shortCircuitAnalysisResultRepository.findFullResults(MAGNITUDE_RESULT_UUID).get();
        List<FeederResultEntity> feederResultEntities = resultMagnitudeEntity.getFaultResults().stream()
            .flatMap(faultResultEntity -> faultResultEntity.getFeederResults().stream())
            .sorted(Comparator.comparing(f1 -> f1.getFeederResultUuid().toString()))
            .toList();
        feederResultEntity1 = feederResultEntities.get(0);
        feederResultEntity2 = feederResultEntities.get(1);
        feederResultEntity3 = feederResultEntities.get(2);
        feederResultEntityMagnitudeList.add(feederResultEntity1);
        feederResultEntityMagnitudeList.add(feederResultEntity2);
        feederResultEntityMagnitudeList.add(feederResultEntity3);

        // Fortescue fault
        shortCircuitAnalysisResultRepository.insert(FORTESCUE_RESULT_UUID, RESULT_FORTESCUE_FULL, MOCK_RUN_CONTEXT, "");
        resultFortescueEntity = shortCircuitAnalysisResultRepository.findFullResults(FORTESCUE_RESULT_UUID).get();
        feederResultEntities = resultFortescueEntity.getFaultResults().stream()
            .flatMap(faultResultEntity -> faultResultEntity.getFeederResults().stream())
            .sorted(Comparator.comparing(f1 -> f1.getFeederResultUuid().toString()))
            .toList();
        feederResultEntity4 = feederResultEntities.get(0);
        feederResultEntity5 = feederResultEntities.get(1);
        feederResultEntity6 = feederResultEntities.get(2);
        feederResultEntityFortescueList.add(feederResultEntity4);
        feederResultEntityFortescueList.add(feederResultEntity5);
        feederResultEntityFortescueList.add(feederResultEntity6);
    }

    @AfterAll
    void tearDown() {
        shortCircuitAnalysisResultRepository.deleteAll();
    }

    @ParameterizedTest(name = "[{index}] Using the filter(s) {1} should return the given entities")
    @MethodSource({
        "provideContainsFilters",
        "provideStartsWithFilters",
        "provideNotEqualFilters",
        "provideNotEqualNestedFieldsFilters",
        "provideLessThanOrEqualFilters",
        "provideGreaterThanOrEqualFilters"
    })
    void feederResultFilterTest(ShortCircuitAnalysisResultEntity resultEntity, List<ResourceFilter> resourceFilters, List<FeederResultEntity> feederList) {
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(resultEntity, resourceFilters, Pageable.unpaged());
        //since the request is unpaged we only care about the result and not the order
        assertThat(feederPage.getContent()).extracting("feederResultUuid").describedAs("Check if the IDs of the feeder page are correct")
            .containsExactlyInAnyOrderElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    @ParameterizedTest(name = "[{index}] Using the pageable {1} should return the given entities")
    @MethodSource({
        "providePageable",
        "provideSortingPageable"
    })
    void feederResultPageableTest(ShortCircuitAnalysisResultEntity resultEntity, Pageable pageable, List<FeederResultEntity> feederList) {
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(resultEntity, null, pageable);
        assertThat(feederPage.getContent()).extracting("feederResultUuid").describedAs("Check if the IDs of the feeder page are correct")
            .containsExactlyElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    @ParameterizedTest(name = "[{index}] Using the filter {1} should throw an exception")
    @MethodSource({
        "provideBadFilter",
    })
    void feederResultFilterExceptionTest(ShortCircuitAnalysisResultEntity resultEntity, List<ResourceFilter> resourceFilters) {
        assertThrows(InvalidDataAccessApiUsageException.class, () -> shortCircuitAnalysisResultRepository.findFeederResultsPage(resultEntity, resourceFilters, Pageable.unpaged()));
    }

    private Stream<Arguments> provideContainsFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_2", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("ID_2")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_4", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("ID_4")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_1", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_3", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("ID_1")
                && feederResultEntity.getConnectableId().contains("ID_3")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "CONN", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("CONN")
                && feederResultEntity.getConnectableId().contains("ID")).toList()),
            // we test escaping of wildcard chars also
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "%", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("%")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "_D_1", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> feederResultEntity.getConnectableId().contains("_D_1")).toList()),
            // case insensitive
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "id_2", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.containsIgnoreCase(feederResultEntity.getConnectableId(), "id_2")).toList())
        );
    }

    private Stream<Arguments> provideStartsWithFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "A_CONN", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.startsWith(feederResultEntity.getConnectableId(), "A_CONN")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.startsWith(feederResultEntity.getConnectableId(), "B_CONN")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "CONN", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.startsWith(feederResultEntity.getConnectableId(), "CONN")).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "A_CONN", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.startsWith(feederResultEntity.getConnectableId(), "A_CONN") && StringUtils.startsWith(feederResultEntity.getConnectableId(), "B_CONN")).toList()),
            // case insensitive
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "a_conn", "connectableId")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> StringUtils.startsWithIgnoreCase(feederResultEntity.getConnectableId(), "a_conn")).toList())
        );
    }

    private Stream<Arguments> provideNotEqualFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 22.17, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getCurrent()).equals(22.17)).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 18.56, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getCurrent()).equals(18.56)).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 22.17, "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 53.94, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getCurrent()).equals(22.17) && !((Double) feederResultEntity.getCurrent()).equals(53.94)).toList())
        );
    }

    private Stream<Arguments> provideNotEqualNestedFieldsFilters() {
        return Stream.of(
            Arguments.of(
                resultFortescueEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 45.328664779663086, "fortescueCurrent.positiveMagnitude")),
                feederResultEntityFortescueList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getFortescueCurrent().getPositiveMagnitude()).equals(45.328664779663086)).toList()),
            Arguments.of(
                resultFortescueEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 42, "fortescueCurrent.positiveMagnitude")),
                feederResultEntityFortescueList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getFortescueCurrent().getPositiveMagnitude()).equals(42d)).toList()),
            Arguments.of(
                resultFortescueEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 45.328664779663086, "fortescueCurrent.positiveMagnitude"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, 18.170874567665456, "fortescueCurrent.positiveMagnitude")),
                feederResultEntityFortescueList.stream().filter(feederResultEntity -> !((Double) feederResultEntity.getFortescueCurrent().getPositiveMagnitude()).equals(45.328664779663086)
                && !((Double) feederResultEntity.getFortescueCurrent().getPositiveMagnitude()).equals(18.170874567665456)).toList())

        );
    }

    private Stream<Arguments> provideLessThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, 22.17, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 22.17) <= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, 18.56, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 18.56) <= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, 53.94, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 53.94) <= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, 22.17, "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, 53.94, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 22.17) <= 0).toList())
        );
    }

    private Stream<Arguments> provideGreaterThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, 22.17, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 22.17) >= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, 18.56, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 18.56) >= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, 53.95, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 53.95) >= 0).toList()),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, 22.17, "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, 53.94, "current")),
                feederResultEntityMagnitudeList.stream().filter(feederResultEntity -> Double.compare(feederResultEntity.getCurrent(), 53.94) >= 0).toList())
        );
    }

    private Stream<Arguments> provideBadFilter() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.NOT_EQUAL, "A_CONN", "connectableId"))),
            Arguments.of(
                resultMagnitudeEntity,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.STARTS_WITH, 22.17, "current")))
        );
    }

    private Stream<Arguments> providePageable() {
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 2),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(1, 2),
                List.of(feederResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 5),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(1, 5),
                List.of())
        );
    }

    private Stream<Arguments> provideSortingPageable() {
        ArrayList<FeederResultEntity> feederResultList = new ArrayList<>() {{
                add(feederResultEntity1);
                add(feederResultEntity2);
                add(feederResultEntity3);
            }};
        List<FeederResultEntity> feederResultListByConnectableId = feederResultList.stream().sorted(Comparator.comparing(FeederResultEntity::getConnectableId)).toList();
        List<FeederResultEntity> feederResultListByConnectableIdDesc = feederResultList.stream().sorted(Comparator.comparing(FeederResultEntity::getConnectableId).reversed()).toList();
        List<FeederResultEntity> feederResultListByCurrent = feederResultList.stream().sorted(Comparator.comparing(FeederResultEntity::getCurrent)).toList();
        List<FeederResultEntity> feederResultListByCurrentDesc = feederResultList.stream().sorted(Comparator.comparing(FeederResultEntity::getCurrent).reversed()).toList();
        return Stream.of(
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 5, Sort.by("connectableId")),
                feederResultListByConnectableId),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 5, Sort.by("connectableId").descending()),
                feederResultListByConnectableIdDesc),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 5, Sort.by("current")),
                feederResultListByCurrent),
            Arguments.of(
                resultMagnitudeEntity,
                PageRequest.of(0, 5, Sort.by("current").descending()),
                feederResultListByCurrentDesc)
        );
    }

}
