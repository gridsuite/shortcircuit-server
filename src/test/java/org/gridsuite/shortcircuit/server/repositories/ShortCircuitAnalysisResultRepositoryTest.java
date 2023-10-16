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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit4.SpringRunner;

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

    static final UUID resultUuid = UUID.randomUUID();

    @Autowired
    private ShortCircuitAnalysisResultRepository resultRepository;

    @BeforeAll
    void setUp() {
        resultRepository.insert(resultUuid, RESULT, Map.of(), false, "");
    }

    @AfterAll
    void tearDown() {
        resultRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource({
        "provideContainsFilters",
        "provideStartsWithFilters",
        "provideNoEqualFilters",
        "provideLessThanOrEqualToFilters",
        "provideGreaterThanOrEqualToFilters"
    })
    void feederResultFilterTest(List<Filter> filters, long count) {
        ShortCircuitAnalysisResultEntity resultEntity = resultRepository.find(resultUuid).get();
        Specification<FeederResultEntity> specification = FeederResultSpecifications.buildSpecification(resultEntity, filters);
        Page<FeederResultEntity> feedersPage = resultRepository.findFeederResultsPage(specification, Pageable.unpaged());
        assertThat(feedersPage.get().count()).isEqualTo(count);
    }

    private static Stream<Arguments> provideContainsFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_1", "connectableId")),
                1),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_2", "connectableId")),
                1),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID_4", "connectableId")),
                0),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "CONN", "connectableId"),
                    new Filter(Filter.DataType.TEXT, Filter.Type.CONTAINS, "ID", "connectableId")),
                3)
        );
    }

    private static Stream<Arguments> provideStartsWithFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "A_CONN", "connectableId")),
                2),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "B_CONN", "connectableId")),
                1),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.TEXT, Filter.Type.STARTS_WITH, "CONN", "connectableId")),
                0)
        );
    }

    private static Stream<Arguments> provideNoEqualFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, 22.17, "current")),
                2),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, 18.57, "current")),
                2),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, 18.56, "current")),
                3),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, 22.17, "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.NOT_EQUAL, 53.94, "current")),
                1)
        );
    }

    private static Stream<Arguments> provideLessThanOrEqualToFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, 22.17, "current")),
                2),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, 18.56, "current")),
                0),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, 53.94, "current")),
                3),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, 22.17, "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.LESS_THAN_OR_EQUAL, 53.94, "current")),
                2)
        );
    }

    private static Stream<Arguments> provideGreaterThanOrEqualToFilters() {
        return Stream.of(
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, 22.17, "current")),
                2),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, 18.56, "current")),
                3),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, 53.95, "current")),
                0),
            Arguments.of(List.of(
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, 22.17, "current"),
                    new Filter(Filter.DataType.NUMBER, Filter.Type.GREATER_THAN_OR_EQUAL, 53.94, "current")),
                1)
        );
    }

}