/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server.repositories;

import com.powsybl.shortcircuit.*;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.gridsuite.shortcircuit.server.TestUtils.assertRequestsCount;

/**
 * @author Etienne HOMER <etienne.homer@rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ShortCircuitResultRepositoryTest {

    FaultResult fault1 = new MagnitudeFaultResult(new BusFault("VLHV1_0", "ELEMENT_ID_1"), 17.0,
            List.of(), List.of(),
            45.3, FaultResult.Status.SUCCESS);
    FaultResult fault2 = new MagnitudeFaultResult(new BusFault("VLHV2_0", "ELEMENT_ID_2"), 18.0,
            List.of(), List.of(),
            47.3, FaultResult.Status.SUCCESS);
    FaultResult fault3 = new MagnitudeFaultResult(new BusFault("VLGEN_0", "ELEMENT_ID_3"), 19.0,
            List.of(), List.of(),
            49.3, FaultResult.Status.SUCCESS);

    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");

    @Autowired
    private ShortCircuitAnalysisResultRepository shortCircuitAnalysisResultRepository;

    @Before
    public void setUp() {
        shortCircuitAnalysisResultRepository.deleteAll();
        SQLStatementCountValidator.reset();
    }

    @Test
    public void deleteResultTest() {
        ShortCircuitAnalysisResult results = new ShortCircuitAnalysisResult(List.of(fault1, fault2, fault3));
        shortCircuitAnalysisResultRepository.insert(RESULT_UUID, results, Map.of(), "OK");
        SQLStatementCountValidator.reset();

        shortCircuitAnalysisResultRepository.delete(RESULT_UUID);

        assertRequestsCount(4, 0, 0, 5);
    }

}
