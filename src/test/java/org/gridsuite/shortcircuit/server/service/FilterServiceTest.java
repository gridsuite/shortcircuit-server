/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.filter.AbstractFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class FilterServiceTest {

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";

    private static final String VARIANT_ID = "variant_id";

    private static final UUID LIST_UUID = UUID.randomUUID();

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private FilterService filterService;

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

        List<UUID> filterUuids = List.of(LIST_UUID);
        List<AbstractFilter> filters = filterService.getFilters(filterUuids);
        assertNotNull(filters);
    }
}
