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
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@FieldNameConstants
@NoArgsConstructor
@Entity
@Table(name = "shortcircuit_result")
public class ShortCircuitAnalysisResultEntity {

    @Id
    private UUID resultUuid;

    @Column(columnDefinition = "timestamptz")
    private OffsetDateTime writeTimeStamp;

    /*
    Bidirectional relation is not needed here and is done for performance
    https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/
    equals() and hashCode() methods are not overrided as the article above recommands it because we use a Set of FaultResultEntity
    and having a constant hashCode() causes performance issues.
     */
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter
    private Set<FaultResultEntity> faultResults;

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, OffsetDateTime writeTimeStamp, Set<FaultResultEntity> faultResults) {
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
