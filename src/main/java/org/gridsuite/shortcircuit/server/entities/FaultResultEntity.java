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
import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class FaultResultEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    private UUID faultResultUuid;

    @Embedded
    private FaultEmbeddable fault;

    @Column
    private double current;

    @Column
    private double shortCircuitPower;

    @ElementCollection
    @CollectionTable(name = "limit_violations")
    private List<LimitViolationEmbeddable> limitViolations;

    @ElementCollection
    @CollectionTable(name = "feeder_results")
    private List<FeederResultEmbeddable> feederResults;

}
