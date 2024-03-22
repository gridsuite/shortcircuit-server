/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import com.powsybl.security.LimitViolationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.experimental.FieldNameConstants;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@FieldNameConstants
public class LimitViolationEmbeddable {

    @Column
    private String subjectId;

    @Column
    @Enumerated(EnumType.STRING)
    private LimitViolationType limitType;

    @Column(name = "limit_value")
    private double limit;

    @Column
    private String limitName;

    @Column(name = "actual_value")
    private double value;
}
