/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class FeederResultEmbeddable {

    @Column
    private String connectableId;

    @Column
    private double current;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "positiveMagnitude", column = @Column(name = "fortescue_current_positive_magnitude")),
        @AttributeOverride(name = "zeroMagnitude", column = @Column(name = "fortescue_current_zero_magnitude")),
        @AttributeOverride(name = "negativeMagnitude", column = @Column(name = "fortescue_current_negative_magnitude")),
        @AttributeOverride(name = "positiveAngle", column = @Column(name = "fortescue_current_positive_angle")),
        @AttributeOverride(name = "zeroAngle", column = @Column(name = "fortescue_current_zero_angle")),
        @AttributeOverride(name = "negativeAngle", column = @Column(name = "fortescue_current_negative_angle")),
        @AttributeOverride(name = "magnitudeA", column = @Column(name = "fortescue_current_magnitude_a")),
        @AttributeOverride(name = "magnitudeB", column = @Column(name = "fortescue_current_magnitude_b")),
        @AttributeOverride(name = "magnitudeC", column = @Column(name = "fortescue_current_magnitude_c")),
        @AttributeOverride(name = "angleA", column = @Column(name = "fortescue_current_angle_a")),
        @AttributeOverride(name = "angleB", column = @Column(name = "fortescue_current_angle_b")),
        @AttributeOverride(name = "angleC", column = @Column(name = "fortescue_current_angle_c")),
    })
    private FortescueResultEmbeddable fortescueCurrent;
}
