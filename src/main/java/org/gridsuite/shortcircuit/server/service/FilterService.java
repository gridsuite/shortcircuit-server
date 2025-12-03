/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.network.store.client.NetworkStoreService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractFilterService;
import org.gridsuite.filter.utils.EquipmentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class FilterService extends AbstractFilterService {

    public FilterService(RestTemplateBuilder restTemplateBuilder,
                         NetworkStoreService networkStoreService,
                         @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(restTemplateBuilder, networkStoreService, filterServerBaseUri);
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        // Get equipment types from violation types
        List<EquipmentType> equipmentTypes = List.of(EquipmentType.VOLTAGE_LEVEL);

        // Call the common implementation with specific parameters
        return super.getResourceFilter(networkUuid, variantId, globalFilter, equipmentTypes, "fault.voltageLevelId");
    }
}
