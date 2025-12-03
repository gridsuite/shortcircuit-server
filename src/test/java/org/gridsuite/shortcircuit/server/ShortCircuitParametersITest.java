package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersEntity;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.shortcircuit.server.ShortCircuitParametersController.DUPLICATE_FROM;
import static org.gridsuite.shortcircuit.server.service.ShortCircuitResultContext.HEADER_BUS_ID;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith({ MockitoExtension.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class ShortCircuitParametersITest implements WithAssertions {
    private static final String USER_ID = "userTestId";
    private static final UUID NETWORK_ID = UUID.randomUUID();
    private final String defaultParametersValuesJson;
    private final String defaultParametersInfosJson;
    private final String someParametersValuesJson;

    public ShortCircuitParametersITest() throws Exception {
        this.defaultParametersValuesJson = Files.readString(Paths.get(this.getClass().getClassLoader().getResource("default_shorcircuit_values_parameters.json").toURI())).replaceAll("\\s+", "");
        this.someParametersValuesJson = Files.readString(Paths.get(this.getClass().getClassLoader().getResource("default_shorcircuit_values_parameters.json").toURI())).replaceAll("\\s+", "")
            .replace("\"minVoltageDropProportionalThreshold\":20.0", "\"minVoltageDropProportionalThreshold\":42.0");
        this.defaultParametersInfosJson = Files.readString(Paths.get(this.getClass().getClassLoader().getResource("default_shorcircuit_infos_parameters.json").toURI())).replaceAll("\\s+", "");
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParametersRepository parametersRepository;

    @MockitoBean
    private NotificationService notificationService; //to keep; for not testing notification part, it's tested in another class test

    @BeforeAll
    void checkDatabaseClean() {
        assertThat(parametersRepository.count()).as("parameters in database").isZero();
    }

    @AfterEach
    void verifyMocks() {
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void runAnalysis() throws Exception {
        runAnalysisTest(req -> { }, headers -> headers, false, defaultParametersValuesJson);
    }

    @Test
    void runAnalysisWithParameters() throws Exception {
        final UUID parametersUuid = parametersRepository.save(ShortCircuitParametersEntity.builder().provider(TestUtils.DEFAULT_PROVIDER).minVoltageDropProportionalThreshold(42.0).build()).getId();
        runAnalysisTest(req -> req.queryParam("parametersUuid", parametersUuid.toString()), headers -> headers, false, someParametersValuesJson);
    }

    @Test
    void runAnalysisWithBusId() throws Exception {
        final String busId = UUID.randomUUID().toString();
        runAnalysisTest(
            req -> req
                    .queryParam("busId", busId),
            headers -> headers.put(HEADER_BUS_ID, busId),
            true,
            defaultParametersValuesJson.replace("\"withFortescueResult\":false", "\"withFortescueResult\":true"));
    }

    /** Save parameters into the repository and return its UUID. */
    protected UUID saveAndReturnId(ShortCircuitParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    private void runAnalysisTest(final Consumer<MockHttpServletRequestBuilder> requestSet, final UnaryOperator<Builder<String, Object>> headerSet, boolean debug, final String response) throws Exception {
        final MockHttpServletRequestBuilder requestBuilder = post("/v1/networks/{networkUuid}/run-and-save", NETWORK_ID).header(HEADER_USER_ID, USER_ID);
        requestSet.accept(requestBuilder);
        requestBuilder.queryParam("debug", String.valueOf(debug));
        final String resultId = StringUtils.strip(mockMvc.perform(requestBuilder)
            .andDo(log())
            .andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string(matchesPattern(TestUtils.UUID_IN_JSON))
            )
            .andReturn()
            .getResponse()
            .getContentAsString(), "\"");
        final ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        //FIXME: to remove when https://github.com/powsybl/powsybl-ws-commons/pull/53 is merged
        verify(notificationService).sendRunMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).as("message sent").usingRecursiveComparison()
            .withEqualsForFields((String j1, String j2) -> {
                //we can have more details than with simple string comparaison
                try {
                    JSONAssert.assertEquals(j2, j1, true);
                    return true;
                } catch (JSONException ex) {
                    log.error("payload not equals", ex);
                    return false;
                }
            }, "payload")
            // message headers use an IdGenerator not overriden
            .withEqualsForFields((UUID id1, UUID id2) -> (id1 == null) == (id2 == null), "headers." + MessageHeaders.ID)
            // we must do a comparaison using AssertJ because message headers have real system timestamp
            .withEqualsForFields((Long t1, Long t2) -> t1 < t2, "headers." + MessageHeaders.TIMESTAMP)
            .isEqualTo(new GenericMessage<>(response, headerSet.apply(ImmutableMap.<String, Object>builder().putAll(Map.of(
                MessageHeaders.ID, UUID.randomUUID(),
                MessageHeaders.TIMESTAMP, System.currentTimeMillis(),
                "resultUuid", resultId,
                "debug", debug,
                // TODO : remove next line when fix in powsybl-ws-commons will handle null provider
                "provider", TestUtils.DEFAULT_PROVIDER,
                "networkUuid", NETWORK_ID.toString(),
                HEADER_USER_ID, USER_ID
            ))).build()));
    }

    @Test
    void deleteParameters() throws Exception {
        final UUID paramsId = parametersRepository.save(TestUtils.createDefaultParametersEntity()).getId();
        mockMvc.perform(delete("/v1/parameters/{id}", paramsId)).andExpectAll(status().isOk(), content().bytes(new byte[0]));
        assertThat(parametersRepository.count()).isZero();
    }

    @Test
    void createParameters() throws Exception {
        mockMvc.perform(post("/v1/parameters").contentType(MediaType.APPLICATION_JSON)
                .content(defaultParametersInfosJson
                    .replace("\"withLoads\":false", "\"withLoads\":true")
                    .replace("\"studyType\":\"TRANSIENT\"", "\"studyType\":\"STEADY_STATE\"") + "}"))
            .andDo(log()).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string(matchesPattern(TestUtils.UUID_IN_JSON))
            ).andReturn();
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringFields("id")
            .isEqualTo(new ShortCircuitParametersEntity(ShortCircuitParametersInfos.builder()
                .provider(TestUtils.DEFAULT_PROVIDER)
                .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
                .commonParameters(ShortCircuitParameters.load()
                    .setStudyType(StudyType.STEADY_STATE)
                    .setMinVoltageDropProportionalThreshold(20.0)
                    .setWithNeutralPosition(true)
                    .setWithVoltageResult(false)
                    .setWithFeederResult(false)
                    .setWithShuntCompensators(false)
                )
                .specificParametersPerProvider(Map.of())
                .build()));
    }

    @Test
    void createDefaultParameters() throws Exception {
        mockMvc.perform(post("/v1/parameters/default")).andDo(log()).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            content().string(matchesPattern(TestUtils.UUID_IN_JSON))
        ).andReturn();
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringFields("id")
            .isEqualTo(TestUtils.createDefaultParametersEntity());
    }

    @Test
    void retrieveParameters() throws Exception {
        final UUID pUuid = saveAndReturnId(ShortCircuitParametersInfos.builder()
            .provider(TestUtils.DEFAULT_PROVIDER)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
            .commonParameters(ShortCircuitParameters.load()
                .setStudyType(StudyType.STEADY_STATE)
                .setMinVoltageDropProportionalThreshold(Math.PI)
                .setWithNeutralPosition(true)
            )
            .specificParametersPerProvider(Map.of())
            .build());

        // build expected response programmatically (more robust than brittle string replacements)
        final ShortCircuitParameters expectedCommon = ShortCircuitParameters.load()
            .setStudyType(StudyType.STEADY_STATE)
            .setMinVoltageDropProportionalThreshold(Math.PI)
            .setWithNeutralPosition(true);

        final Map<String, Object> expected = new java.util.HashMap<>();
        expected.put("predefinedParameters", "ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP");
        expected.put("specificParametersPerProvider", Map.of());
        expected.put("commonParameters", expectedCommon);
        expected.put("cei909VoltageRanges", ShortCircuitParametersConstants.CEI909_VOLTAGE_PROFILE);

        final String expectedJson = objectMapper.writeValueAsString(expected);

        mockMvc.perform(get("/v1/parameters/{id}", pUuid)).andDo(log()).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            content().json(expectedJson, false) // non-strict JSON comparison (order independent)
        );
    }

    @Test
    void resetParameters() throws Exception {
        final UUID pUuid = saveAndReturnId(ShortCircuitParametersInfos.builder()
            .provider(TestUtils.DEFAULT_PROVIDER)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
            .commonParameters(ShortCircuitParameters.load()
                .setStudyType(StudyType.STEADY_STATE)
                .setMinVoltageDropProportionalThreshold(Math.PI)
            )
            .specificParametersPerProvider(Map.of())
            .build());
        mockMvc.perform(put("/v1/parameters/{id}", pUuid)).andDo(log()).andExpectAll(
            status().isOk(),
            content().bytes(new byte[0])
        );
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringFields("id")
            .isEqualTo(TestUtils.createDefaultParametersEntity()
        );
    }

    @Test
    void updateParameters() throws Exception {
        final UUID pUuid = saveAndReturnId(ShortCircuitParametersInfos.builder()
            .provider(TestUtils.DEFAULT_PROVIDER)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
            .commonParameters(ShortCircuitParameters.load()
                .setStudyType(StudyType.STEADY_STATE)
                .setMinVoltageDropProportionalThreshold(Math.PI)
            )
            .specificParametersPerProvider(null)
            .build());

        mockMvc.perform(put("/v1/parameters/{id}", pUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(defaultParametersInfosJson
                    .replace("\"withLoads\":false", "\"withLoads\":true")
                    .replace("\"studyType\":\"TRANSIENT\"", "\"studyType\":\"STEADY_STATE\"") + "}"))
            .andDo(log()).andExpectAll(status().isOk(), content().bytes(new byte[0]))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringFields("id")
            .isEqualTo(new ShortCircuitParametersEntity(ShortCircuitParametersInfos.builder()
                .provider(TestUtils.DEFAULT_PROVIDER)
                .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
                .commonParameters(ShortCircuitParameters.load()
                    .setStudyType(StudyType.STEADY_STATE)
                    .setMinVoltageDropProportionalThreshold(20.0)
                    .setWithNeutralPosition(true)
                    .setWithVoltageResult(false)
                    .setWithFeederResult(false)
                    .setWithShuntCompensators(false)
                )
                .specificParametersPerProvider(null)
                .build()));
    }

    @Test
    void duplicateParameters() throws Exception {
        final ShortCircuitParametersInfos infos = ShortCircuitParametersInfos.builder()
            .provider(TestUtils.DEFAULT_PROVIDER)
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909)
            .commonParameters(ShortCircuitParameters.load()
                .setWithLimitViolations(false)
                .setWithFeederResult(false)
                .setWithVoltageResult(false)
                .setMinVoltageDropProportionalThreshold(1.234)
                .setWithLoads(false)
                .setWithShuntCompensators(false)
                .setWithVSCConverterStations(false)
            )
            .specificParametersPerProvider(Map.of())
            .build();
        final UUID pUuid = saveAndReturnId(infos);
        final UUID pUuidDuplicated = objectMapper.readValue(mockMvc.perform(post("/v1/parameters").queryParam(DUPLICATE_FROM, pUuid.toString()))
            .andDo(log()).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string(matchesPattern(TestUtils.UUID_IN_JSON))
            ).andReturn().getResponse().getContentAsByteArray(), UUID.class);
        final ShortCircuitParametersEntity originalEntity = infos.toEntity();
        originalEntity.setId(pUuid);
        final ShortCircuitParametersEntity duplicatedEntity = infos.toEntity();
        duplicatedEntity.setId(pUuidDuplicated);
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringCollectionOrder()
            .isEqualTo(List.of(
                originalEntity,
                duplicatedEntity
            ));
    }

    @ParameterizedTest
    @MethodSource("testWithInvalidParametersArgArgs")
    void testWithInvalidParametersArg(final RequestBuilder requestBuilder, final ResultMatcher statusResult, final boolean throwsException, final Integer expectedStatus) throws Exception {
        if (!throwsException) {
            mockMvc.perform(requestBuilder).andDo(log())
                    .andExpectAll(statusResult, content().bytes(new byte[0]));
        } else {
            mockMvc.perform(requestBuilder).andDo(log())
                    .andExpectAll(statusResult, content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
                            jsonPath("$.status").value(expectedStatus),
                            jsonPath("$.server").exists(),
                            jsonPath("$.path").exists(),
                            jsonPath("$.detail").exists(),
                            jsonPath("$.timestamp").exists());
        }
    }

    private static Stream<Arguments> testWithInvalidParametersArgArgs() {
        return Stream.of(
            Arguments.of(get("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound(), false, null),
            Arguments.of(delete("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound(), false, null),
            Arguments.of(post("/v1/parameters"), status().isBadRequest(), true, 400),
            Arguments.of(post("/v1/parameters").content("{}"), status().isBadRequest(), true, 400),
            Arguments.of(post("/v1/parameters").contentType(MediaType.TEXT_PLAIN).content("{}"), status().isBadRequest(), true, 400),
            Arguments.of(post("/v1/parameters").queryParam(DUPLICATE_FROM, ""), status().isBadRequest(), true, 400),
            Arguments.of(post("/v1/parameters").queryParam(DUPLICATE_FROM, UUID.randomUUID().toString()), status().isNotFound(), false, null),
            Arguments.of(put("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound(), false, null)
        );
    }

    @Test
    void testgetProvider() throws Exception {
        UUID parametersUuid = saveAndReturnId(ShortCircuitParametersInfos.builder()
            .provider("SC_PROVIDER")
            .predefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP)
            .commonParameters(ShortCircuitParameters.load()
                .setStudyType(StudyType.STEADY_STATE)
                .setMinVoltageDropProportionalThreshold(Math.PI)
            )
            .specificParametersPerProvider(Map.of())
            .build());

        MvcResult result = mockMvc.perform(get(
                        "/v1/parameters/{parametersUuid}/provider", parametersUuid))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("SC_PROVIDER");
    }
}
