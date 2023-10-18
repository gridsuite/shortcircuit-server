package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import org.gridsuite.shortcircuit.server.dto.Filter;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitAnalysisResultEntity;
import org.gridsuite.shortcircuit.server.utils.FeederResultSpecifications;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest // would be better with @DataJpaTest but does not work here
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // improve tests speed as we only read DB
class ShortCircuitAnalysisResultRepositoryTest {

    static final FeederResult FEEDER_RESULT_1 = new MagnitudeFeederResult("A_CONN_ID_1", 22.17);
    static final FeederResult FEEDER_RESULT_2 = new MagnitudeFeederResult("A_CONN_ID_2", 18.57);
    static final FeederResult FEEDER_RESULT_3 = new MagnitudeFeederResult("B_CONN_ID_3", 53.94);
    static final FaultResult FAULT_RESULT = new MagnitudeFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
        List.of(FEEDER_RESULT_1, FEEDER_RESULT_2, FEEDER_RESULT_3), List.of(),
        47.3, FaultResult.Status.SUCCESS);
    static final ShortCircuitAnalysisResult RESULT = new ShortCircuitAnalysisResult(List.of(FAULT_RESULT));
    static final UUID RESULT_UUID = UUID.randomUUID();

    private FeederResultEntity feederResultEntity1;
    private FeederResultEntity feederResultEntity2;
    private FeederResultEntity feederResultEntity3;
    private ShortCircuitAnalysisResultEntity resultEntity;

    @Autowired
    private ShortCircuitAnalysisResultRepository shortCircuitAnalysisResultRepository;

    @BeforeAll
    void setUp() {
        shortCircuitAnalysisResultRepository.insert(RESULT_UUID, RESULT, Map.of(), false, "");
        resultEntity = shortCircuitAnalysisResultRepository.findFullResults(RESULT_UUID).get();
        List<FeederResultEntity> feederResultEntities = resultEntity.getFaultResults().stream()
            .flatMap(faultResultEntity -> faultResultEntity.getFeederResults().stream())
            .sorted(Comparator.comparing(FeederResultEntity::getConnectableId))
            .toList();
        feederResultEntity1 = feederResultEntities.get(0);
        feederResultEntity2 = feederResultEntities.get(1);
        feederResultEntity3 = feederResultEntities.get(2);
    }

    @AfterAll
    void tearDown() {
        shortCircuitAnalysisResultRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource({
        "provideContainsFilters",
        "provideStartsWithFilters",
        "provideNotEqualFilters",
        "provideLessThanOrEqualFilters",
        "provideGreaterThanOrEqualFilters"
    })
    void feederResultFilterTest(List<Filter> filters, List<FeederResultEntity> feederList) {
        Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(RESULT_UUID, filters);
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(specification, Pageable.unpaged());
        assertThat(feederPage.getContent()).extracting("feederResultUuid")
            .containsExactlyElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    @ParameterizedTest
    @MethodSource({
        "providePageable",
        "provideSortingPageable"
    })
    void feederResultPageableTest(Pageable pageable, List<FeederResultEntity> feederList) {
        Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(RESULT_UUID, null);
        Page<FeederResultEntity> feederPage = shortCircuitAnalysisResultRepository.findFeederResultsPage(specification, pageable);
        assertThat(feederPage.getContent()).extracting("feederResultUuid")
            .containsExactlyElementsOf(feederList.stream().map(FeederResultEntity::getFeederResultUuid).toList());
    }

    private Stream<Arguments> provideContainsFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_2", "connectableId")),
                List.of(feederResultEntity2)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_4", "connectableId")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_1", "connectableId"),
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_3", "connectableId")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "CONN", "connectableId"),
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID", "connectableId")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            // we test escaping of wildcard chars also
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "%", "connectableId")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "_D_1", "connectableId")),
                List.of())
        );
    }

    private Stream<Arguments> provideStartsWithFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "A_CONN", "connectableId")),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                List.of(feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "CONN", "connectableId")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "A_CONN", "connectableId"),
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                List.of())
        );
    }

    private Stream<Arguments> provideNotEqualFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, "22.17", "current")),
                List.of(feederResultEntity2, feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, "18.56", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, "22.17", "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, "53.94", "current")),
                List.of(feederResultEntity2))
        );
    }

    private Stream<Arguments> provideLessThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, "22.17", "current")),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, "18.56", "current")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, "22.17", "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity1, feederResultEntity2))
        );
    }

    private Stream<Arguments> provideGreaterThanOrEqualFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, "22.17", "current")),
                List.of(feederResultEntity1, feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, "18.56", "current")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, "53.95", "current")),
                List.of()),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, "22.17", "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, "53.94", "current")),
                List.of(feederResultEntity3))
        );
    }

    private Stream<Arguments> providePageable() {
        return Stream.of(
            Arguments.of(
                PageRequest.of(0, 2),
                List.of(feederResultEntity1, feederResultEntity2)),
            Arguments.of(
                PageRequest.of(1, 2),
                List.of(feederResultEntity3)),
            Arguments.of(
                PageRequest.of(0, 5),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                PageRequest.of(1, 5),
                List.of())
        );
    }

    private Stream<Arguments> provideSortingPageable() {
        return Stream.of(
            Arguments.of(
                PageRequest.of(0, 5, Sort.by("connectableId")),
                List.of(feederResultEntity1, feederResultEntity2, feederResultEntity3)),
            Arguments.of(
                PageRequest.of(0, 5, Sort.by("connectableId").descending()),
                List.of(feederResultEntity3, feederResultEntity2, feederResultEntity1)),
            Arguments.of(
                PageRequest.of(0, 5, Sort.by("current")),
                List.of(feederResultEntity2, feederResultEntity1, feederResultEntity3)),
            Arguments.of(
                PageRequest.of(0, 5, Sort.by("current").descending()),
                List.of(feederResultEntity3, feederResultEntity1, feederResultEntity2))
        );
    }

}
