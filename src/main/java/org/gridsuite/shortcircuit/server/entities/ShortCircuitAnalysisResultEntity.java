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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private Page<FaultResultEntity> faultResultsPage;

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, ZonedDateTime writeTimeStamp, Page<FaultResultEntity> faultResults) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addFaultResults(faultResults);
    }

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, ZonedDateTime writeTimeStamp, Set<FaultResultEntity> faultResults) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addFaultResults(faultResults);
    }

    public void addFaultResults(Set<FaultResultEntity> faultResults) {
        if (faultResults != null) {
            Page<FaultResultEntity> newFaultResultsPage = new PageImpl<>(faultResults.stream().collect(Collectors.toList()));
            this.faultResultsPage = newFaultResultsPage;
            faultResultsPage.forEach(f -> f.setResult(this));
        }
    }

    public void addFaultResults(Page<FaultResultEntity> faultResultsPage) {
        if (faultResultsPage != null) {
            this.faultResultsPage = faultResultsPage;
            faultResultsPage.forEach(f -> f.setResult(this));
        }
    }
}
