/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
public class FaultResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID faultResultUuid;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Setter
    private ShortCircuitAnalysisResultEntity result;

    @Embedded
    private FaultEmbeddable fault;

    @Column
    private double current;

    @Column
    private double shortCircuitPower;

    // Must save it in database to get pageable FaultResultEntity request filtered by number of LimitViolations
    @Column
    private int nbLimitViolations;

    @ElementCollection
    @CollectionTable(name = "limit_violations",
            indexes = {@Index(name = "limit_violations_fault_result_idx",
                    columnList = "fault_result_entity_fault_result_uuid")})
    private List<LimitViolationEmbeddable> limitViolations;

    @ElementCollection
    @CollectionTable(name = "feeder_results",
            indexes = {@Index(name = "feeder_results_fault_result_idx",
                    columnList = "fault_result_entity_fault_result_uuid")})
    private List<FeederResultEmbeddable> feederResults;

    public FaultResultEntity(FaultEmbeddable fault, double current, double shortCircuitPower, List<LimitViolationEmbeddable> limitViolations, List<FeederResultEmbeddable> feederResults) {
        this.fault = fault;
        this.current = current;
        this.shortCircuitPower = shortCircuitPower;
        this.limitViolations = limitViolations;
        this.nbLimitViolations = limitViolations.size();
        this.feederResults = feederResults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FaultResultEntity)) {
            return false;
        }
        return faultResultUuid != null && faultResultUuid.equals(((FaultResultEntity) o).getFaultResultUuid());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
