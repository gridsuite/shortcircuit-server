package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.shortcircuit.*;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.gridsuite.shortcircuit.server.utils.FeederResultSpecifications;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest // would be better with @DataJpaTest but does not work here
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // improve tests speed as we only read DB
class ShortCircuitAnalysisResultRepositoryTest {

    static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("A_CONN_ID_1", 22.17);
    static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("A_CONN_ID_2", 18.57);
    static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("B_CONN_ID_3", 53.94);
    static final FeederResult FEEDER_RESULT_4 = new FortescueFeederResult("A_CONN_ID_4", new FortescueValue(45.328664779663086, -12.69657966563543, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    static final FeederResult FEEDER_RESULT_5 = new FortescueFeederResult("B_CONN_ID_5", new FortescueValue(52.568678656325887, -33.09862779008776, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    static final FeederResult FEEDER_RESULT_6 = new FortescueFeederResult("B_CONN_ID_6", new FortescueValue(18.170874567665456, -90.29865576554445, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    static final LimitViolation LIMIT_VIOLATION_1 = new LimitViolation("SUBJECT_1", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 25.63, 4f, 33.54);
    static final LimitViolation LIMIT_VIOLATION_2 = new LimitViolation("SUBJECT_2", LimitViolationType.LOW_SHORT_CIRCUIT_CURRENT, 12.17, 2f, 10.56);
    static final LimitViolation LIMIT_VIOLATION_3 = new LimitViolation("SUBJECT_3", LimitViolationType.HIGH_SHORT_CIRCUIT_CURRENT, 45.12, 5f, 54.3);

    static final FaultResult FAULT_RESULT_1 = new MagnitudeFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
        List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(),
        47.3, FaultResult.Status.SUCCESS);
    static final FaultResult FAULT_RESULT_2 = new FortescueFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
        List.of(FEEDER_RESULT_4, FEEDER_RESULT_5, FEEDER_RESULT_6), List.of(LIMIT_VIOLATION_1, LIMIT_VIOLATION_2, LIMIT_VIOLATION_3),
        new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), new FortescueValue(21.328664779663086, -80.73799896240234, Double.NaN, Double.NaN, Double.NaN, Double.NaN), Collections.emptyList(), null, FaultResult.Status.SUCCESS);

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

    @Autowired
    private ShortCircuitAnalysisResultRepository shortCircuitAnalysisResultRepository;

    @BeforeAll
    void setUp() {
        // Magnitude fault
        shortCircuitAnalysisResultRepository.insert(MAGNITUDE_RESULT_UUID, RESULT_MAGNITUDE_FULL, Map.of(), false, "");
        ShortCircuitAnalysisResultEntity resultEntity = shortCircuitAnalysisResultRepository.findFullResults(MAGNITUDE_RESULT_UUID).get();
        List<FeederResultEntity> feederResultEntities = resultEntity.getFaultResults().stream()
            .flatMap(faultResultEntity -> faultResultEntity.getFeederResults().stream())
            .sorted(Comparator.comparing(FeederResultEntity::getConnectableId))
            .toList();
        feederResultEntity1 = feederResultEntities.get(0);
        feederResultEntity2 = feederResultEntities.get(1);
        feederResultEntity3 = feederResultEntities.get(2);
        // Fortescue fault
        shortCircuitAnalysisResultRepository.insert(FORTESCUE_RESULT_UUID, RESULT_FORTESCUE_FULL, Map.of(), true, "");
        resultEntity = shortCircuitAnalysisResultRepository.findFullResults(FORTESCUE_RESULT_UUID).get();
        feederResultEntities = resultEntity.getFaultResults().stream()
            .flatMap(faultResultEntity -> faultResultEntity.getFeederResults().stream())
            .sorted(Comparator.comparing(FeederResultEntity::getConnectableId))
            .toList();
        feederResultEntity4 = feederResultEntities.get(0);
        feederResultEntity5 = feederResultEntities.get(1);
        feederResultEntity6 = feederResultEntities.get(2);
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
    void feederResultFilterTest(UUID resultUuid, List<ResourceFilter> resourceFilters, List<FeederResultEntity> feederList) {
        Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(resultUuid, resourceFilters);
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(specification, Pageable.unpaged());
        assertThat(feederPage.getContent()).extracting("feederResultUuid").describedAs("Check if the IDs of the feeder page are correct")
            .containsExactlyElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    @ParameterizedTest(name = "[{index}] Using the pageable {1} should return the given entities")
    @MethodSource({
        "providePageable",
        "provideSortingPageable"
    })
    void feederResultPageableTest(UUID resultUuid, Pageable pageable, List<FeederResultEntity> feederList) {
        Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(resultUuid, null);
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(specification, pageable);
        assertThat(feederPage.getContent()).extracting("feederResultUuid").describedAs("Check if the IDs of the feeder page are correct")
            .containsExactlyElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    private Stream<Arguments> provideContainsFilters() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_2", "connectableId")),
                List.of(feederResultEntity2)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_4", "connectableId")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_1", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID_3", "connectableId")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "CONN", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "ID", "connectableId")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            // we test escaping of wildcard chars also
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "%", "connectableId")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.CONTAINS, "_D_1", "connectableId")),
                List.of())
        );
    }

    private Stream<Arguments> provideStartsWithFilters() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "A_CONN", "connectableId")),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                List.of(feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "CONN", "connectableId")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "A_CONN", "connectableId"),
                    new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                List.of())
        );
    }

    private Stream<Arguments> provideNotEqualFilters() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "22.17", "current")),
                List.of(feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "18.56", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "22.17", "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "53.94", "current")),
                List.of(feederResultEntity2))
        );
    }

    private Stream<Arguments> provideNotEqualNestedFieldsFilters() {
        return Stream.of(
            Arguments.of(
                FORTESCUE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "45.328664779663086", "fortescueCurrent.positiveMagnitude")),
                List.of(feederResultEntity5, feederResultEntity6)),
            Arguments.of(
                FORTESCUE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "42", "fortescueCurrent.positiveMagnitude")),
                List.of(feederResultEntity4, feederResultEntity5, feederResultEntity6)),
            Arguments.of(
                FORTESCUE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "45.328664779663086", "fortescueCurrent.positiveMagnitude"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.NOT_EQUAL, "18.170874567665456", "fortescueCurrent.positiveMagnitude")),
                List.of(feederResultEntity5))
        );
    }

    private Stream<Arguments> provideLessThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, "22.17", "current")),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, "18.56", "current")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, "22.17", "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.LESS_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity1, feederResultEntity2))
        );
    }

    private Stream<Arguments> provideGreaterThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, "22.17", "current")),
                List.of(feederResultEntity1, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, "18.56", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, "53.95", "current")),
                List.of()),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                List.of(
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, "22.17", "current"),
                    new ResourceFilter(ResourceFilter.DataType.NUMBER, ResourceFilter.Type.GREATER_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity3))
        );
    }

    private Stream<Arguments> providePageable() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 2),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(1, 2),
                List.of(feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 5),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(1, 5),
                List.of())
        );
    }

    private Stream<Arguments> provideSortingPageable() {
        return Stream.of(
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 5, Sort.by("connectableId")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 5, Sort.by("connectableId").descending()),
                List.of(feederResultEntity3, feederResultEntity2, feederResultEntity1)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 5, Sort.by("current")),
                List.of(feederResultEntity2, feederResultEntity1, feederResultEntity3)),
            Arguments.of(
                MAGNITUDE_RESULT_UUID,
                PageRequest.of(0, 5, Sort.by("current").descending()),
                List.of(feederResultEntity3, feederResultEntity1, feederResultEntity2))
        );
    }

}
