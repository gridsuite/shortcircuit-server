/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.RestTemplateConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.gridsuite.shortcircuit.server.service.ShortCircuitService.CEI909_VOLTAGE_PROFILE;

@ContextConfiguration(classes = { RestTemplateConfig.class, ShortCircuitParametersInfosTest.TestConfig.class })
@JsonTest
class ShortCircuitParametersInfosTest implements WithAssertions {
    private static final String DUMB_JSON = "\"predefinedParameters\":\"ICC_MAX_WITH_CEI909\", \"parameters\":{\"version\":\"1.4\",\"withLimitViolations\":true,\"withVoltageResult\":true,\"withFeederResult\":true,\"studyType\":\"TRANSIENT\",\"minVoltageDropProportionalThreshold\":0.0,\"withFortescueResult\":false,\"withLoads\":true,\"withShuntCompensators\":true,\"withVSCConverterStations\":true,\"withNeutralPosition\":false,\"initialVoltageProfileMode\":\"NOMINAL\",\"detailedReport\":true}";

    @Autowired
    private ObjectMapper objectMapper;

    @SneakyThrows(JSONException.class)
    private static JSONObject toJson(@NonNull final VoltageRange voltageRange) {
        return new JSONObject().put("minimumNominalVoltage", voltageRange.getMinimumNominalVoltage())
                               .put("maximumNominalVoltage", voltageRange.getMaximumNominalVoltage())
                               .put("voltageRangeCoefficient", voltageRange.getRangeCoefficient());
    }

    @Test
    void shouldSerializeCei909VoltageRanges() throws Exception {
        final String jsonSerialized = objectMapper.writeValueAsString(new ShortCircuitParametersInfos(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters()));
        JSONAssert.assertEquals(
            new JSONObject().put("predefinedParameters", ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909.toString())
                            .put("parameters", new JSONObject().put("version", "1.4"))
                            .put("cei909VoltageRanges", CEI909_VOLTAGE_PROFILE.stream()
                                    .map(ShortCircuitParametersInfosTest::toJson)
                                    .reduce(new JSONArray(), JSONArray::put, (arr1, arr2) -> null)),
            new JSONObject(jsonSerialized),
            JSONCompareMode.STRICT_ORDER //extensible comparaison to not have to list others fields
        );
    }

    @Test
    void shouldIgnoreCei909VoltageRangesWhenDeserializeJsonWithVoltageRange() {
        assertThatNoException().as("DTO with CEI909 field")
            .isThrownBy(() -> objectMapper.readValue("{" + DUMB_JSON + ", \"cei909VoltageRanges\":[null,null]}", ShortCircuitParametersInfos.class));
    }

    @Test
    void shouldIgnoreCei909VoltageRangesWhenDeserializeJsonWithoutVoltageRange() {
        assertThatNoException().as("DTO without CEI909 field")
            .isThrownBy(() -> objectMapper.readValue("{" + DUMB_JSON + "}", ShortCircuitParametersInfos.class));
    }

    // Needed to get a RestTemplateBuilder since we don't use the sprintboot context in this test class
    @Configuration
    static class TestConfig {
        @Bean
        public RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }
    }
}
