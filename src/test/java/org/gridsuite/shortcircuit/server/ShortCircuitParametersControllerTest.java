/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server;

import lombok.NonNull;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.service.ShortCircuitParametersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({ MockitoExtension.class })
@WebMvcTest(controllers = { ShortCircuitParametersController.class })
class ShortCircuitParametersControllerTest implements WithAssertions {
    private final String defaultParametersJson;

    public ShortCircuitParametersControllerTest() throws Exception {
        this.defaultParametersJson = Files.readString(Paths.get(this.getClass().getResource(this.getClass().getSimpleName() + ".json").toURI())).replaceAll("\\s+", "");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortCircuitParametersService shortCircuitParametersService;

    @AfterEach
    void checkMocks() {
        Mockito.verifyNoMoreInteractions(shortCircuitParametersService);
    }

    @Test
    void testGetExistingParameters() throws Exception {
        final UUID arg = UUID.randomUUID();
        final Optional<ShortCircuitParametersInfos> returned = Optional.of(new ShortCircuitParametersInfos(null, null, null));
        when(shortCircuitParametersService.getParameters(any(UUID.class))).thenReturn(returned);
        mockMvc.perform(get("/v1/parameters/{pUuid}", arg.toString()))
               .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON), content().json(defaultParametersJson));
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).getParameters(uuidCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg);
    }

    @Test
    void testGetNonExistingParameters() throws Exception {
        final UUID arg = UUID.randomUUID();
        when(shortCircuitParametersService.getParameters(any(UUID.class))).thenReturn(Optional.empty());
        mockMvc.perform(get("/v1/parameters/{pUuid}", arg.toString()))
               .andExpectAll(status().isNotFound());
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).getParameters(uuidCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg);
    }

    @Test
    void testCreateParameters() throws Exception {
        final UUID returned = UUID.randomUUID();
        when(shortCircuitParametersService.createParameters(any(ShortCircuitParametersInfos.class))).thenReturn(returned);
        mockMvc.perform(post("/v1/parameters").content(defaultParametersJson).contentType(MediaType.APPLICATION_JSON))
               .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON), content().string("\"" + returned + "\""));
        final ArgumentCaptor<ShortCircuitParametersInfos> dtoCaptor = ArgumentCaptor.forClass(ShortCircuitParametersInfos.class);
        verify(shortCircuitParametersService).createParameters(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue()).isEqualTo(new ShortCircuitParametersInfos(null, null, null));
    }

    @Test
    void testCreateDefaultParameters() throws Exception {
        final UUID returned = UUID.randomUUID();
        when(shortCircuitParametersService.createDefaultParameters()).thenReturn(returned);
        mockMvc.perform(post("/v1/parameters/default"))
               .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON), content().string("\"" + returned + "\""));
        verify(shortCircuitParametersService).createDefaultParameters();
    }

    @Test
    void testDuplicateExistingParameters() throws Exception {
        final UUID arg = UUID.randomUUID();
        final UUID returned = UUID.randomUUID();
        when(shortCircuitParametersService.duplicateParameters(any(UUID.class))).thenReturn(Optional.of(returned));
        mockMvc.perform(post("/v1/parameters").param("duplicateFrom", arg.toString()))
               .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON), content().string("\"" + returned + "\""));
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).duplicateParameters(uuidCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg);
    }

    @Test
    void testDuplicateNonExistingParameters() throws Exception {
        final UUID arg = UUID.randomUUID();
        when(shortCircuitParametersService.duplicateParameters(any(UUID.class))).thenReturn(Optional.empty());
        mockMvc.perform(post("/v1/parameters").param(ShortCircuitParametersController.DUPLICATE_FROM, arg.toString()))
               .andExpectAll(status().isNotFound());
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).duplicateParameters(uuidCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg);
    }

    private static Stream<Arguments> testParametersArgs() {
        return Stream.of(
            Arguments.arguments(true, status().isOk()),
            Arguments.arguments(false, status().isNotFound())
        );
    }

    @MethodSource("testParametersArgs")
    @ParameterizedTest
    void testDeleteParameters(final boolean existing, @NonNull final ResultMatcher statusMatcher) throws Exception {
        final UUID arg = UUID.randomUUID();
        when(shortCircuitParametersService.deleteParameters(any(UUID.class))).thenReturn(existing);
        mockMvc.perform(delete("/v1/parameters/{pUuid}", arg.toString()))
               .andExpectAll(statusMatcher);
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).deleteParameters(uuidCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg);
    }

    @MethodSource("testParametersArgs")
    @ParameterizedTest
    void testUpdateParameters(final boolean existing, @NonNull final ResultMatcher statusMatcher) throws Exception {
        final UUID arg1 = UUID.randomUUID();
        if (!existing) {
            doThrow(new NoSuchElementException()).when(shortCircuitParametersService).updateParameters(any(UUID.class), any(ShortCircuitParametersInfos.class));
        }
        mockMvc.perform(put("/v1/parameters/{pUuid}", arg1.toString()).content(defaultParametersJson).contentType(MediaType.APPLICATION_JSON))
               .andExpect(statusMatcher);
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<ShortCircuitParametersInfos> dtoCaptor = ArgumentCaptor.forClass(ShortCircuitParametersInfos.class);
        verify(shortCircuitParametersService).updateParameters(uuidCaptor.capture(), dtoCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg1);
        assertThat(dtoCaptor.getValue()).isEqualTo(new ShortCircuitParametersInfos(null, null, null));
    }

    @MethodSource("testParametersArgs")
    @ParameterizedTest
    void testResetParameters(final boolean existing, @NonNull final ResultMatcher statusMatcher) throws Exception {
        final UUID arg1 = UUID.randomUUID();
        if (!existing) {
            doThrow(new NoSuchElementException()).when(shortCircuitParametersService).updateParameters(any(UUID.class), nullable(ShortCircuitParametersInfos.class));
        }

        mockMvc.perform(put("/v1/parameters/{pUuid}", arg1.toString()))
               .andExpectAll(statusMatcher);
        final ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shortCircuitParametersService).updateParameters(uuidCaptor.capture(), isNull());
        assertThat(uuidCaptor.getValue()).isEqualTo(arg1);
    }
}
