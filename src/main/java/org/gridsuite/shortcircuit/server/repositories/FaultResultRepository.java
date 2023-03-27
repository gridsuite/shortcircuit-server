/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.repositories;

import org.gridsuite.shortcircuit.server.entities.FaultResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import javax.persistence.QueryHint;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com
 */
@Repository
public interface FaultResultRepository extends JpaRepository<FaultResultEntity, UUID> {
    // cf.https://vladmihalcea.com/jpql-distinct-jpa-hibernate/
    @Query("Select DISTINCT fr from FaultResultEntity fr join ShortCircuitAnalysisResultEntity r on r.resultUuid = :resultUuid left join fetch fr.limitViolations limit_violations")
    @QueryHints(value = {
        @QueryHint(name = org.hibernate.jpa.QueryHints.HINT_PASS_DISTINCT_THROUGH, value = "false")
    })
    List<FaultResultEntity> findByResultUuidWithLimitViolations(@Param("resultUuid") UUID resultUuid);

    @Query("Select DISTINCT fr from FaultResultEntity fr join ShortCircuitAnalysisResultEntity r on r.resultUuid = :resultUuid left join fetch fr.feederResults fdr ")
    @QueryHints(value = {
        @QueryHint(name = org.hibernate.jpa.QueryHints.HINT_PASS_DISTINCT_THROUGH, value = "false")
    })
    List<FaultResultEntity> findByResultUuidWithFeederResults(@Param("resultUuid") UUID resultUuid);
}
