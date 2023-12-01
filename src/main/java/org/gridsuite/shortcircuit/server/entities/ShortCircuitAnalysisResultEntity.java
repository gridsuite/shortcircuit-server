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

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL)
    @Setter
    private Set<FaultResultEntity> faultResults = new HashSet<>();

    public ShortCircuitAnalysisResultEntity(UUID resultUuid, ZonedDateTime writeTimeStamp, Set<FaultResultEntity> faultResults) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addFaultResults(faultResults);
    }

    public void addFaultResults(Set<FaultResultEntity> faultResults) {
        if (faultResults != null) {
            this.faultResults.addAll(faultResults);
            faultResults.forEach(f -> f.setResult(this));
        }
    }

//    @Override
//    public boolean equals(Object o) {
//        if (this == o) {
//            return true;
//        }
//        if (!(o instanceof ShortCircuitAnalysisResultEntity)) {
//            return false;
//        }
//        return resultUuid != null && resultUuid.equals(((ShortCircuitAnalysisResultEntity) o).getResultUuid());
//    }
//
//    @Override
//    public int hashCode() {
//        return getClass().hashCode();
//    }
    //https://stackoverflow.com/questions/74981727/spring-data-jpa-one-to-many-takes-more-time-when-saving
}
