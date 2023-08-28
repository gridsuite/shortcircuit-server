/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.shortcircuit.server;

import org.springframework.cloud.stream.binder.test.OutputDestination;

import java.util.List;

import static org.junit.Assert.assertNull;

/**
 * @author Franck Lecuyer <frnck.lecuyer at rte-france.com>
 */
public final class TestUtils {

    private static final long TIMEOUT = 100;

    private TestUtils() {
    }

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> assertNull("Should not be any messages in queue " + destination + " : ", output.receive(TIMEOUT, destination)));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }
}
