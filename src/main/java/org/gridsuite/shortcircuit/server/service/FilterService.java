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

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractFilterService;
import org.gridsuite.filter.identifierlistfilter.FilterEquipments;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@Slf4j
public class FilterService extends AbstractFilterService {
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";
    private static final String NETWORK_UUID = "networkUuid";

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

    public List<FilterEquipments> getFilterHvdcStationFromHvdc(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        return getFilterEquipments("/filters/export/hvdcStations", filterUuids, networkUuid, variantId);
    }

    public List<FilterEquipments> getFilterBusIds(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        return getFilterEquipments("/filters/export/busIds", filterUuids, networkUuid, variantId);
    }

    public List<FilterEquipments> getFilterEquipments(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        return getFilterEquipments("/filters/export", filterUuids, networkUuid, variantId);
    }

    public List<FilterEquipments> getFilterEquipments(String url, List<UUID> filterUuids, UUID networkUuid, String variantId) {
        Objects.requireNonNull(filterUuids);
        Objects.requireNonNull(networkUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + url)
                .queryParam(IDS, filterUuids)
                .queryParam(NETWORK_UUID, networkUuid.toString());
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.build().toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<FilterEquipments>>() {
                }).getBody();
    }

    public List<IdentifiableAttributes> getIdentifiablesFromFilters(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        List<FilterEquipments> filterEquipments = getFilterEquipments(filterUuids, networkUuid, variantId);

        List<IdentifiableAttributes> mergedIdentifiables = new ArrayList<>();
        for (FilterEquipments filterEquipment : filterEquipments) {
            mergedIdentifiables.addAll(filterEquipment.getIdentifiableAttributes());
        }

        return mergedIdentifiables;
    }
}
