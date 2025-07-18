/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import lombok.NonNull;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class FilterService extends AbstractFilterService {

    public FilterService(
            NetworkStoreService networkStoreService,
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(networkStoreService, filterServerBaseUri);
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        // Get equipment types from violation types
        List<EquipmentType> equipmentTypes = List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);

        // Call the common implementation with specific parameters
        return super.getResourceFilter(networkUuid, variantId, globalFilter, equipmentTypes, FeederResultEntity.Fields.connectableId);
    }
}
