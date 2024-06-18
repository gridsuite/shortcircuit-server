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
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ContextConfiguration;

import static org.gridsuite.shortcircuit.server.service.ShortCircuitService.CEI909_VOLTAGE_PROFILE;

@ContextConfiguration(classes = { RestTemplateConfig.class })
@JsonTest
class ShortCircuitParametersInfosTest implements WithAssertions {
    private static final String DUMB_JSON = "\"predefinedParameters\":\"ICC_MAX_WITH_CEI909\", \"parameters\":{\"version\":\"1.3\",\"withLimitViolations\":true,\"withVoltageResult\":true,\"withFeederResult\":true,\"studyType\":\"TRANSIENT\",\"minVoltageDropProportionalThreshold\":0.0,\"withFortescueResult\":false,\"withLoads\":true,\"withShuntCompensators\":true,\"withVSCConverterStations\":true,\"withNeutralPosition\":false,\"initialVoltageProfileMode\":\"NOMINAL\",\"detailedReport\":true}";

    @Autowired
    ObjectMapper objectMapper;

    @SneakyThrows
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
                            .put("parameters", new JSONObject().put("version", "1.3")
                                                               .put("withLimitViolations", true)
                                                               .put("withVoltageResult", true)
                                                               .put("withFeederResult", true)
                                                               .put("studyType", "TRANSIENT")
                                                               .put("minVoltageDropProportionalThreshold", 0.0)
                                                               //.put("withFortescueResult", true)
                                                               .put("withLoads", true)
                                                               .put("withShuntCompensators", true)
                                                               .put("withVSCConverterStations", true)
                                                               .put("withNeutralPosition", false)
                                                               .put("initialVoltageProfileMode", "NOMINAL")
                                                               .put("detailedReport", true))
                            .put("cei909VoltageRanges", CEI909_VOLTAGE_PROFILE.stream()
                                    .map(ShortCircuitParametersInfosTest::toJson)
                                    .reduce(new JSONArray(), JSONArray::put, (arr1, arr2) -> null)),
            new JSONObject(jsonSerialized),
            JSONCompareMode.STRICT
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
}
