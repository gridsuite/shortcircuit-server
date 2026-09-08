/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.powsybl.commons.report.ReportNodeDeserializer;
import com.powsybl.commons.report.ReportNodeJsonModule;
import com.powsybl.shortcircuit.json.ShortCircuitAnalysisJsonModule;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> restClientBuilder.messageConverters(messageConverters -> {
            //find and replace Jackson message converter with our own
            for (int i = 0; i < messageConverters.size(); i++) {
                final HttpMessageConverter<?> httpMessageConverter = messageConverters.get(i);
                if (httpMessageConverter instanceof MappingJackson2HttpMessageConverter) {
                    messageConverters.set(i, mappingJackson2HttpMessageConverter());
                }
            }
        });
    }

    private MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public static ObjectMapper objectMapper() {
        var objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.registerModule(new ShortCircuitAnalysisJsonModule());
        objectMapper.registerModule(new ReportNodeJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReportNodeDeserializer.DICTIONARY_VALUE_ID, null));
        return objectMapper;
    }
}
