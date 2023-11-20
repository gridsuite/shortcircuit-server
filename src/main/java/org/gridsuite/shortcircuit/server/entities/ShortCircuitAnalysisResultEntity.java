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

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "shortcircuit_result")
public class ShortCircuitAnalysisResultEntity {

    @Id
    private UUID resultUuid;

    @Column
    private ZonedDateTime writeTimeStamp;

    @OneToMany(mappedBy = "result", cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    }
    )
    // https://vladmihalcea.com/how-to-batch-delete-statements-with-hibernate/
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Setter
    private Set<FaultResultEntity> faultResults;

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, ZonedDateTime writeTimeStamp, Set<FaultResultEntity> faultResults) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addFaultResults(faultResults);
    }

    public void addFaultResults(Set<FaultResultEntity> faultResults) {
        if (faultResults != null) {
            this.faultResults = faultResults;
            faultResults.forEach(f -> f.setResult(this));
        }
    }
}
