/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(indexes = {
    @Index(name = "result_uuid_nbLimitViolations_idx", columnList = "result_result_uuid, nbLimitViolations"),
    @Index(name = "result_uuid_idx", columnList = "result_result_uuid")
})
public class FaultResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID faultResultUuid;

    @ManyToOne(fetch = FetchType.LAZY)
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

    @Column
    private double ipMax;

    @Column
    private double ipMin;

    @Column
    private Double deltaCurrentIpMin;

    @Column
    private Double deltaCurrentIpMax;
    public FaultResultEntity(FaultEmbeddable fault, double current, double shortCircuitPower, List<LimitViolationEmbeddable> limitViolations, List<FeederResultEntity> feederResults, double ipMin, double ipMax, FortescueResultEmbeddable fortescueCurrent, FortescueResultEmbeddable fortescueVoltage, double deltaCurrentIpMin, double deltaCurrentIpMax) {
        this.fault = fault;
        this.current = current;
        this.shortCircuitPower = shortCircuitPower;
        if (limitViolations != null) {
            this.nbLimitViolations = limitViolations.size();
        }
        this.ipMin = ipMin;
        this.ipMax = ipMax;
        this.deltaCurrentIpMin = deltaCurrentIpMin;
        this.deltaCurrentIpMax = deltaCurrentIpMax;
    }it s

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

    public double getPositiveMagnitude() {
        return Double.NaN;
    }
}
