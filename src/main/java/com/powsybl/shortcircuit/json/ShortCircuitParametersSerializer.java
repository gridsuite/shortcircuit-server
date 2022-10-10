/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.shortcircuit.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.io.IOException;

import static com.powsybl.shortcircuit.ShortCircuitConstants.*;

/**
 * @author Boubakeur Brahimi
 */
//TODO: to be removed when fix is done in powsybl-core
public class ShortCircuitParametersSerializer extends StdSerializer<ShortCircuitParameters> {

    public ShortCircuitParametersSerializer() {
        super(ShortCircuitParameters.class);
    }

    @Override
    public void serialize(ShortCircuitParameters parameters, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("version", ShortCircuitParameters.VERSION);
        JsonUtil.writeOptionalBooleanField(jsonGenerator, "withLimitViolations", parameters.isWithLimitViolations(), DEFAULT_WITH_LIMIT_VIOLATIONS);
        JsonUtil.writeOptionalBooleanField(jsonGenerator, "withVoltageMap", parameters.isWithVoltageMap(), DEFAULT_WITH_VOLTAGE_MAP);
        JsonUtil.writeOptionalBooleanField(jsonGenerator, "withFeederResult", parameters.isWithFeederResult(), DEFAULT_WITH_FEEDER_RESULT);
        jsonGenerator.writeStringField("studyType", parameters.getStudyType().name());
        JsonUtil.writeOptionalDoubleField(jsonGenerator, "minVoltageDropProportionalThreshold", parameters.getMinVoltageDropProportionalThreshold());
        JsonUtil.writeExtensions(parameters, jsonGenerator, serializerProvider, JsonShortCircuitParameters.getExtensionSerializers());
        jsonGenerator.writeEndObject();
    }

}
