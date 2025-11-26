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
import org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.test.context.ContextConfiguration;

import static org.gridsuite.shortcircuit.server.entities.parameters.ShortCircuitParametersConstants.CEI909_VOLTAGE_PROFILE;

import java.util.Collections;

@ContextConfiguration(classes = {RestTemplateConfig.class})
//NOTE: this surprises given the name AutoConfigureWebClient, but this is not actually web clients,
// it is builders for webclients, and it's not just webflux webclient,
// it's also resttemplatebuilder that we need.
// And other builders are also registered but without consequences, they're just unused.
// In the future springboot 4.0.0 is supposed to have changed the name to be less surprising
@AutoConfigureWebClient
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
        final String jsonSerialized = objectMapper.writeValueAsString(new ShortCircuitParametersInfos(ShortCircuitParametersConstants.DEFAULT_PROVIDER, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909, new ShortCircuitParameters(), Collections.emptyMap()));
        JSONAssert.assertEquals(
            new JSONObject().put("predefinedParameters", ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909.toString())
                            .put("commonParameters", new JSONObject().put("version", "1.4"))
                            .put("specificParametersPerProvider", new JSONObject())
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

}
