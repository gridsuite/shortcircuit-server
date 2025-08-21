/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
class FilterServiceTest {

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";
    private static final String VARIANT_ID = "variant_id";
    private static final UUID LIST_UUID = UUID.randomUUID();
    private static final Object TEST_FILTERS = List.of(createTestExpertFilter());

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private FilterService filterService;

    private static ExpertFilter createTestExpertFilter() {
        AbstractExpertRule simpleRule = NumberExpertRule.builder()
                .value(220.0)
                .field(FieldType.NOMINAL_VOLTAGE)
                .operator(OperatorType.EQUALS)
                .build();
        return new ExpertFilter(FilterServiceTest.LIST_UUID, new Date(), EquipmentType.VOLTAGE_LEVEL, simpleRule);
    }

    @BeforeEach
    void setUp(final MockWebServer mockWebServer) throws Exception {
        filterService = new FilterService(networkStoreService, initMockWebServer(mockWebServer));
        doNothing().when(variantManager).setWorkingVariant(anyString());
    }

    private String initMockWebServer(final MockWebServer server) throws IOException {
        String jsonFiltersExpected = objectMapper.writeValueAsString(TEST_FILTERS);
        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String requestPath = Objects.requireNonNull(request.getPath());
                if (requestPath.matches("/v1/filters/metadata\\?ids=.*")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonFiltersExpected);
                } else {
                    return new MockResponse.Builder().code(HttpStatus.NOT_FOUND.value()).body("Path not supported: " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        return baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
    }

    @Test
    void testGetResourceFiltersWithAllFilters() {
        // Test case with all types of filters
        GlobalFilter globalFilter = GlobalFilter.builder()
                .genericFilter(List.of(LIST_UUID))
                .nominalV(List.of("220.0", "400.0"))
                .countryCode(List.of(Country.FR, Country.DE))
                .substationProperty(Map.of("prop1", List.of("value1", "value2")))
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(UUID.class), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                globalFilter
        );

        assertNotNull(result);
        if (result.isPresent()) {
            ResourceFilterDTO dto = result.get();
            assertEquals(ResourceFilterDTO.DataType.TEXT, dto.dataType());
            assertEquals(ResourceFilterDTO.Type.IN, dto.type());
            assertEquals("fault.voltageLevelId", dto.column());
        }
    }

    @Test
    void testGetResourceFiltersEmptyResult() {
        // Test case when no filters match
        GlobalFilter emptyGlobalFilter = GlobalFilter.builder()
                .genericFilter(List.of())
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                emptyGlobalFilter
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetResourceFiltersWithGenericFilters() {
        // Test case with generic filters
        GlobalFilter globalFilter = GlobalFilter.builder()
                .genericFilter(List.of(LIST_UUID))
                .nominalV(List.of("220.0"))
                .countryCode(List.of(Country.FR))
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(UUID.class), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                globalFilter
        );

        assertNotNull(result);
    }

    @Test
    void testGetFilters() {
        List<AbstractFilter> result = filterService.getFilters(List.of());
        assertTrue(result.isEmpty());

        List<AbstractFilter> filters = filterService.getFilters(List.of(LIST_UUID));
        assertNotNull(filters);
    }
}
