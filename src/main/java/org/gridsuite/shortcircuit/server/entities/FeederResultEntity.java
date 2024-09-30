/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import com.powsybl.iidm.network.ThreeSides;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
@FieldNameConstants
@Table(name = "feeder_results",
        indexes = {@Index(name = "feeder_results_fault_result_idx",
                columnList = "fault_result_entity_fault_result_uuid")})
public class FeederResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID feederResultUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fault_result_entity_fault_result_uuid")
    private FaultResultEntity faultResult;

    @Column
    private String connectableId;

    @Column
    private double current;

    @Column
    @Enumerated(EnumType.STRING)
    private ThreeSides side;

    @Embedded
    @AttributeOverride(name = "positiveMagnitude", column = @Column(name = "fortescue_current_positive_magnitude"))
    @AttributeOverride(name = "zeroMagnitude", column = @Column(name = "fortescue_current_zero_magnitude"))
    @AttributeOverride(name = "negativeMagnitude", column = @Column(name = "fortescue_current_negative_magnitude"))
    @AttributeOverride(name = "positiveAngle", column = @Column(name = "fortescue_current_positive_angle"))
    @AttributeOverride(name = "zeroAngle", column = @Column(name = "fortescue_current_zero_angle"))
    @AttributeOverride(name = "negativeAngle", column = @Column(name = "fortescue_current_negative_angle"))
    @AttributeOverride(name = "magnitudeA", column = @Column(name = "fortescue_current_magnitude_a"))
    @AttributeOverride(name = "magnitudeB", column = @Column(name = "fortescue_current_magnitude_b"))
    @AttributeOverride(name = "magnitudeC", column = @Column(name = "fortescue_current_magnitude_c"))
    @AttributeOverride(name = "angleA", column = @Column(name = "fortescue_current_angle_a"))
    @AttributeOverride(name = "angleB", column = @Column(name = "fortescue_current_angle_b"))
    @AttributeOverride(name = "angleC", column = @Column(name = "fortescue_current_angle_c"))
    private FortescueResultEmbeddable fortescueCurrent;

    public double getPositiveMagnitude() {
        return this.getFortescueCurrent() != null ? this.getFortescueCurrent().getPositiveMagnitude() : Double.NaN;
    }

    public void setFaultResult(FaultResultEntity faultResult) {
        this.faultResult = faultResult;
    }

    public FeederResultEntity(String connectableId, double current, FortescueResultEmbeddable fortescueCurrent, ThreeSides side) {
        this.connectableId = connectableId;
        this.current = current;
        this.fortescueCurrent = fortescueCurrent;
        this.side = side;
    }
}
