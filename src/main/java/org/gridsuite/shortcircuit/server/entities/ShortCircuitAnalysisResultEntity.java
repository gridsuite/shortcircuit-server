/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.HashSet;
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

    @JsonInclude()
    @Transient
    private Set<FaultResultEntity> faultResults;

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, ZonedDateTime writeTimeStamp, Set<FaultResultEntity> faultResults) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addFaultResults(faultResults);
    }

    public void addFaultResult(FaultResultEntity faultResult) {
        if (this.faultResults == null) {
            this.faultResults = new HashSet<>();
        }
        faultResults.add(faultResult);
        faultResult.setResult(this);
    }

    public void addFaultResults(Set<FaultResultEntity> faultResultsSet) {
        if (this.faultResults == null) {
            this.faultResults = new HashSet<>();
        }
        faultResults.addAll(faultResultsSet);
        faultResultsSet.stream().forEach(f -> f.setResult(this));
    }

    public void removeFaultResult(FaultResultEntity faultResult) {
        if (this.faultResults == null) {
            return;
        }
        faultResults.remove(faultResult);
        faultResult.setResult(null);
    }

    public void removeFaultResults(Set<FaultResultEntity> faultResultsSet) {
        if (this.faultResults == null) {
            return;
        }
        faultResults.removeAll(faultResultsSet);
        faultResultsSet.stream().forEach(f -> f.setResult(null));
    }
}
