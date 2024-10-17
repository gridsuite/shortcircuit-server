package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.ws.commons.computation.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.shortcircuit.server.entities.ShortCircuitParametersEntity;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.gridsuite.shortcircuit.server.service.ShortCircuitService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.powsybl.ws.commons.computation.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.shortcircuit.server.ShortCircuitParametersController.DUPLICATE_FROM;
import static org.gridsuite.shortcircuit.server.service.ShortCircuitResultContext.HEADER_BUS_ID;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private final String defaultParametersJson;

    public ShortCircuitParametersITest() throws Exception {
        this.defaultParametersJson = Files.readString(Paths.get(this.getClass().getClassLoader().getResource("default_shorcircuit_parameters.json").toURI())).replaceAll("\\s+", "");
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParametersRepository parametersRepository;

    @MockBean
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
        runAnalysisTest(req -> { }, headers -> headers, defaultParametersJson);
    }

    @Test
    void runAnalysisWithParameters() throws Exception {
        final UUID paramsId = parametersRepository.save(new ShortCircuitParametersEntity()).getId();
        runAnalysisTest(req -> req.queryParam("parametersId", paramsId.toString()), headers -> headers, defaultParametersJson);
    }

    @Test
    void runAnalysisWithBusId() throws Exception {
        final String busId = UUID.randomUUID().toString();
        runAnalysisTest(
            req -> req.queryParam("busId", busId),
            headers -> headers.put(HEADER_BUS_ID, busId),
            defaultParametersJson.replace("\"withFortescueResult\":false", "\"withFortescueResult\":true"));
    }

    private void runAnalysisTest(final Consumer<MockHttpServletRequestBuilder> requestSet, final UnaryOperator<Builder<String, Object>> headerSet, final String response) throws Exception {
        final MockHttpServletRequestBuilder requestBuilder = post("/v1/networks/{networkUuid}/run-and-save", NETWORK_ID).header(HEADER_USER_ID, USER_ID);
        requestSet.accept(requestBuilder);
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
                "networkUuid", NETWORK_ID.toString(),
                HEADER_USER_ID, USER_ID
            ))).build()));
    }

    @Test
    void deleteParameters() throws Exception {
        final UUID paramsId = parametersRepository.save(new ShortCircuitParametersEntity()).getId();
        mockMvc.perform(delete("/v1/parameters/{id}", paramsId)).andExpectAll(status().isNoContent(), content().bytes(new byte[0]));
        assertThat(parametersRepository.count()).isZero();
    }

    @Test
    void createParameters() throws Exception {
        final UUID pUuid = objectMapper.readValue(mockMvc.perform(post("/v1/parameters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"predefinedParameters\":\"ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP\",\"parameters\":" + defaultParametersJson
                    .replace("\"withLoads\":false", "\"withLoads\":true")
                    .replace("\"studyType\":\"TRANSIENT\"", "\"studyType\":\"STEADY_STATE\"") + "}"))
            .andDo(log()).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string(matchesPattern(TestUtils.UUID_IN_JSON))
            ).andReturn().getResponse().getContentAsByteArray(), UUID.class);
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .isEqualTo(new ShortCircuitParametersEntity(pUuid, true, false, true, StudyType.STEADY_STATE, 20.0, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP,
                    true, false, true, true, InitialVoltageProfileMode.NOMINAL));
    }

    @Test
    void createDefaultParameters() throws Exception {
        final UUID pUuid = objectMapper.readValue(mockMvc.perform(post("/v1/parameters/default")).andDo(log()).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            content().string(matchesPattern(TestUtils.UUID_IN_JSON))
        ).andReturn().getResponse().getContentAsByteArray(), UUID.class);
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .isEqualTo(new ShortCircuitParametersEntity().setId(pUuid));
    }

    @Test
    void retrieveParameters() throws Exception {
        final UUID pId = parametersRepository.save(new ShortCircuitParametersEntity(true, true, true, StudyType.STEADY_STATE, Math.PI,
            ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP, true, true, true, true, InitialVoltageProfileMode.NOMINAL)).getId();
        mockMvc.perform(get("/v1/parameters/{id}", pId)).andDo(log()).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            content().json("{\"predefinedParameters\":\"ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP\",\"parameters\":" + defaultParametersJson
                .replace("false", "true")
                .replace("\"minVoltageDropProportionalThreshold\":20.0", "\"minVoltageDropProportionalThreshold\":3.141592653589793")
                .replace("\"studyType\":\"TRANSIENT\"", "\"studyType\":\"STEADY_STATE\"")
                + ",\"cei909VoltageRanges\":" + objectMapper.writeValueAsString(ShortCircuitService.CEI909_VOLTAGE_PROFILE) + "}", true)
        );
    }

    @Test
    void resetParameters() throws Exception {
        final UUID pId = parametersRepository.save(new ShortCircuitParametersEntity(true, true, true, StudyType.STEADY_STATE, Math.PI,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP, true, true, true, true, InitialVoltageProfileMode.NOMINAL)).getId();
        mockMvc.perform(put("/v1/parameters/{id}", pId)).andDo(log()).andExpectAll(
            status().isNoContent(),
            content().bytes(new byte[0])
        );
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .isEqualTo(new ShortCircuitParametersEntity().setId(pId));
    }

    @Test
    void updateParameters() throws Exception {
        final UUID pUuid = parametersRepository.save(new ShortCircuitParametersEntity(true, true, true, StudyType.STEADY_STATE, Math.PI,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP, true, true, true, true, InitialVoltageProfileMode.NOMINAL)).getId();
        mockMvc.perform(put("/v1/parameters/{id}", pUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"predefinedParameters\":\"ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP\",\"parameters\":" + defaultParametersJson
                    .replace("\"withLoads\":false", "\"withLoads\":true")
                    .replace("\"studyType\":\"TRANSIENT\"", "\"studyType\":\"STEADY_STATE\"") + "}"))
            .andDo(log()).andExpectAll(status().isNoContent(), content().bytes(new byte[0]))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .singleElement().as("parameters entity")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .isEqualTo(new ShortCircuitParametersEntity(pUuid, true, false, true, StudyType.STEADY_STATE, 20.0, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP,
                        true, false, true, true, InitialVoltageProfileMode.NOMINAL));
    }

    @Test
    void duplicateParameters() throws Exception {
        final Supplier<ShortCircuitParametersEntity> generatorEntity = () -> new ShortCircuitParametersEntity(false, false, false, StudyType.TRANSIENT, 1.234,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, false, false, false, false, InitialVoltageProfileMode.NOMINAL);
        final UUID pUuid = parametersRepository.save(generatorEntity.get()).getId();
        final UUID pUuidDuplicated = objectMapper.readValue(mockMvc.perform(post("/v1/parameters").queryParam(DUPLICATE_FROM, pUuid.toString()))
            .andDo(log()).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string(matchesPattern(TestUtils.UUID_IN_JSON))
            ).andReturn().getResponse().getContentAsByteArray(), UUID.class);
        assertThat(parametersRepository.findAll()).as("parameters in database")
            .usingRecursiveComparison() //because JPA entities haven't equals implemented
            .ignoringCollectionOrder()
            .isEqualTo(List.of(
                generatorEntity.get().setId(pUuid),
                generatorEntity.get().setId(pUuidDuplicated)
            ));
    }

    @ParameterizedTest
    @MethodSource("testWithInvalidParametersArgArgs")
    void testWithInvalidParametersArg(final RequestBuilder requestBuilder, final ResultMatcher statusResult) throws Exception {
        mockMvc.perform(requestBuilder).andDo(log())
                .andExpectAll(statusResult, content().bytes(new byte[0]));
    }

    private static Stream<Arguments> testWithInvalidParametersArgArgs() {
        return Stream.of(
            Arguments.of(get("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound()),
            Arguments.of(delete("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound()),
            Arguments.of(post("/v1/parameters"), status().isBadRequest()),
            Arguments.of(post("/v1/parameters").content("{}"), status().isBadRequest()),
            Arguments.of(post("/v1/parameters").contentType(MediaType.TEXT_PLAIN).content("{}"), status().isBadRequest()),
            Arguments.of(post("/v1/parameters").queryParam(DUPLICATE_FROM, ""), status().isBadRequest()),
            Arguments.of(post("/v1/parameters").queryParam(DUPLICATE_FROM, UUID.randomUUID().toString()), status().isNotFound()),
            Arguments.of(put("/v1/parameters/{parametersUuid}", UUID.randomUUID()), status().isNotFound())
        );
    }
}
