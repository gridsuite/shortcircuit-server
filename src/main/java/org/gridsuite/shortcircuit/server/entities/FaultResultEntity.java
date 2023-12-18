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

    @ElementCollection
    @CollectionTable(name = "limit_violations",
            indexes = {@Index(name = "limit_violations_fault_result_idx",
                    columnList = "fault_result_entity_fault_result_uuid")})
    private List<LimitViolationEmbeddable> limitViolations;

    /*
    Bidirectional relation is not needed here and is done for performance
    https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/
     */
    @OneToMany(
            mappedBy = "faultResult",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<FeederResultEntity> feederResults;

    @Column
    private double ipMax;

    @Column
    private double ipMin;

    @Column
    private Double deltaCurrentIpMin;

    @Column
    private Double deltaCurrentIpMax;

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

    @Embedded
    @AttributeOverride(name = "positiveMagnitude", column = @Column(name = "fortescue_voltage_positive_magnitude"))
    @AttributeOverride(name = "zeroMagnitude", column = @Column(name = "fortescue_voltage_zero_magnitude"))
    @AttributeOverride(name = "negativeMagnitude", column = @Column(name = "fortescue_voltage_negative_magnitude"))
    @AttributeOverride(name = "positiveAngle", column = @Column(name = "fortescue_voltage_positive_angle"))
    @AttributeOverride(name = "zeroAngle", column = @Column(name = "fortescue_voltage_zero_angle"))
    @AttributeOverride(name = "negativeAngle", column = @Column(name = "fortescue_voltage_negative_angle"))
    @AttributeOverride(name = "magnitudeA", column = @Column(name = "fortescue_voltage_magnitude_a"))
    @AttributeOverride(name = "magnitudeB", column = @Column(name = "fortescue_voltage_magnitude_b"))
    @AttributeOverride(name = "magnitudeC", column = @Column(name = "fortescue_voltage_magnitude_c"))
    @AttributeOverride(name = "angleA", column = @Column(name = "fortescue_voltage_angle_a"))
    @AttributeOverride(name = "angleB", column = @Column(name = "fortescue_voltage_angle_b"))
    @AttributeOverride(name = "angleC", column = @Column(name = "fortescue_voltage_angle_c"))
    private FortescueResultEmbeddable fortescueVoltage;

    public FaultResultEntity(FaultEmbeddable fault, double current, double shortCircuitPower, List<LimitViolationEmbeddable> limitViolations, List<FeederResultEntity> feederResults, double ipMin, double ipMax, FortescueResultEmbeddable fortescueCurrent, FortescueResultEmbeddable fortescueVoltage, double deltaCurrentIpMin, double deltaCurrentIpMax) {
        this.fault = fault;
        this.current = current;
        this.shortCircuitPower = shortCircuitPower;
        if (limitViolations != null) {
            this.limitViolations = limitViolations;
            this.nbLimitViolations = limitViolations.size();
        }
        this.ipMin = ipMin;
        this.ipMax = ipMax;
        this.fortescueCurrent = fortescueCurrent;
        this.fortescueVoltage = fortescueVoltage;
        this.deltaCurrentIpMin = deltaCurrentIpMin;
        this.deltaCurrentIpMax = deltaCurrentIpMax;
        if (feederResults != null) {
            setFeederResults(feederResults);
        }
    }

    public void setFeederResults(List<FeederResultEntity> feederResults) {
        this.feederResults = feederResults;
        feederResults.stream().forEach(feederResultEntity -> feederResultEntity.setFaultResult(this));
    }

    public double getPositiveMagnitude() {
        return this.getFortescueCurrent() != null ? this.getFortescueCurrent().getPositiveMagnitude() : Double.NaN;
    }
}
