/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import org.gridsuite.shortcircuit.server.dto.ResourceFilter;

import java.util.UUID;

import static org.gridsuite.shortcircuit.server.dto.FeederResult.CONNECTABLE_ID_COL;
import static org.gridsuite.shortcircuit.server.dto.ResourceFilter.DataType.TEXT;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
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

    public FeederResultEntity(String connectableId, double current, FortescueResultEmbeddable fortescueCurrent) {
        this.connectableId = connectableId;
        this.current = current;
        this.fortescueCurrent = fortescueCurrent;
    }

    public boolean match(ResourceFilter filter) {
        // FeederResultEntity may only be filtered through connectableId
        if (filter.column().equals(CONNECTABLE_ID_COL) &&
            filter.dataType() == TEXT) {
            switch (filter.type()) {
                case EQUALS: return connectableId.equals(filter.value().toString());
                case CONTAINS : return connectableId.contains(filter.value().toString());
                case STARTS_WITH: return connectableId.startsWith(filter.value().toString());
                case NOT_EQUAL: return !connectableId.equals(filter.value().toString());
                default:
                    break;
            }
        }

        return false;
    }
}
