/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import com.powsybl.shortcircuit.ShortCircuitConstants;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.junit.jupiter.api.Test;

class AnalysisParametersEntityTest implements WithAssertions {
    @Test
    void testUpdateSimple() {
        AnalysisParametersEntity entity1 = new AnalysisParametersEntity();
        AnalysisParametersEntity entity2 = new AnalysisParametersEntity().setMinVoltageDropProportionalThreshold(Double.MAX_VALUE);
        assertThat(entity1).as("verification").usingRecursiveComparison().isNotEqualTo(entity2);
        entity1.updateWith(entity2);
        assertThat(entity1).as("check").usingRecursiveComparison().isEqualTo(entity2);
    }

    @Test
    void testUpdate2() {
        AnalysisParametersEntity entity1 = new AnalysisParametersEntity();
        AnalysisParametersEntity entity2 = new AnalysisParametersEntity(
                ShortCircuitConstants.DEFAULT_WITH_LIMIT_VIOLATIONS,
                ShortCircuitConstants.DEFAULT_WITH_VOLTAGE_RESULT,
                ShortCircuitConstants.DEFAULT_WITH_FORTESCUE_RESULT,
                ShortCircuitConstants.DEFAULT_WITH_FEEDER_RESULT,
                ShortCircuitConstants.DEFAULT_STUDY_TYPE,
                ShortCircuitConstants.DEFAULT_MIN_VOLTAGE_DROP_PROPORTIONAL_THRESHOLD,
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909,
                ShortCircuitConstants.DEFAULT_WITH_LOADS,
                ShortCircuitConstants.DEFAULT_WITH_SHUNT_COMPENSATORS,
                ShortCircuitConstants.DEFAULT_WITH_VSC_CONVERTER_STATIONS,
                ShortCircuitConstants.DEFAULT_WITH_NEUTRAL_POSITION,
                ShortCircuitConstants.DEFAULT_INITIAL_VOLTAGE_PROFILE_MODE
                //ShortCircuitConstants.DEFAULT_SUB_TRANSIENT_COEFFICIENT
                //ShortCircuitConstants.DEFAULT_DETAILED_REPORT
        );
        assertThat(entity1).as("verification").usingRecursiveComparison().isNotEqualTo(entity2);
        entity1.updateWith(entity2);
        assertThat(entity1).as("check").usingRecursiveComparison().isEqualTo(entity2);
    }
}
