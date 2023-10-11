/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.utils;

import org.gridsuite.shortcircuit.server.dto.FilterModel;
import org.gridsuite.shortcircuit.server.dto.NumberFilter;
import org.gridsuite.shortcircuit.server.dto.TextFilter;
import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.gridsuite.shortcircuit.server.entities.FeederResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class FeederResultSpecifications {

    public static Specification<FeederResultEntity> faultResultUuidEquals(UUID id) {
        return (feederResult, cq, cb) -> cb.equal(feederResult.get("faultResult").get("faultResultUuid"), id);
    }

    public static Specification<FeederResultEntity> connectableIdContains(String text) {
        return (feederResult, cq, cb) -> cb.like(feederResult.get("connectableId"), "%" + text + "%");
    }

    public static Specification<FeederResultEntity> connectableIdStartsWith(String text) {
        return (feederResult, cq, cb) -> cb.like(feederResult.get("connectableId"), text + "%");
    }

    public static Specification<FeederResultEntity> currentNotEqual(Double number) {
        return (feederResult, cq, cb) -> cb.notEqual(feederResult.get("current"), number);
    }

    public static Specification<FeederResultEntity> currentLessThanOrEqualTo(Double number) {
        return (feederResult, cq, cb) -> cb.lessThanOrEqualTo(feederResult.get("current"), number);
    }

    public static Specification<FeederResultEntity> currentGreaterThanOrEqualTo(Double number) {
        return (feederResult, cq, cb) -> cb.greaterThanOrEqualTo(feederResult.get("current"), number);
    }

    public static Specification<FeederResultEntity> buildSpecification(FaultResultEntity faultResult, FilterModel filterModel) {
        Specification<FeederResultEntity> specification = Specification.where(faultResultUuidEquals(faultResult.getFaultResultUuid()));

        if (filterModel == null) {
            return specification;
        }

        if (filterModel.connectableId() != null) {
            TextFilter connectableIdFilter = filterModel.connectableId();
            switch (connectableIdFilter.type()) {
                case CONTAINS ->
                        specification = specification.and(connectableIdContains(connectableIdFilter.filter()));
                case STARTS_WITH ->
                        specification = specification.and(connectableIdStartsWith(connectableIdFilter.filter()));
            }
        }

        if (filterModel.current() != null) {
            NumberFilter currentFilter = filterModel.current();
            switch (currentFilter.type()) {
                case NOT_EQUAL ->
                        specification = specification.and(currentNotEqual(currentFilter.filter()));
                case LESS_THAN_OR_EQUAL ->
                        specification = specification.and(currentLessThanOrEqualTo(currentFilter.filter()));
                case GREATER_THAN_OR_EQUAL ->
                        specification = specification.and(currentGreaterThanOrEqualTo(currentFilter.filter()));
            }
        }

        return specification;
    }
}
