/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class FortescueResultEmbeddable {

    double positiveMagnitude;

    double zeroMagnitude;

    double negativeMagnitude;

    double positiveAngle;

    double zeroAngle;

    double negativeAngle;

    double magnitudeA;

    double magnitudeB;

    double magnitudeC;

    double angleA;

    double angleB;

    double angleC;
}
