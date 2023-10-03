/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Reporter;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.RecursiveAssertionAssert;
import org.assertj.core.api.RecursiveComparisonAssert;
import org.assertj.core.api.recursive.assertion.RecursiveAssertionConfiguration;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;

/**
 * Assertions for {@link Reporter}.
 *
 * @param <SELF> the "self" type of this assertion class.
 * @param <ACTUAL> the type of {@link Reporter}
 */
public abstract class AbstractReporterAssert<SELF extends AbstractReporterAssert<SELF, ACTUAL>, ACTUAL extends Reporter> extends AbstractAssert<SELF, ACTUAL> {
    protected AbstractReporterAssert(ACTUAL actual, Class<? extends SELF> selfType) {
        super(actual, selfType);
    }

    @Override
    public RecursiveComparisonAssert<?> usingRecursiveComparison(RecursiveComparisonConfiguration recursiveComparisonConfiguration) {
        return super.usingRecursiveComparison(recursiveComparisonConfiguration);
    }

    @Override
    public RecursiveComparisonAssert<?> usingRecursiveComparison() {
        return super.usingRecursiveComparison();
    }

    @Override
    public RecursiveAssertionAssert usingRecursiveAssertion(RecursiveAssertionConfiguration recursiveAssertionConfiguration) {
        return super.usingRecursiveAssertion(recursiveAssertionConfiguration);
    }

    @Override
    public RecursiveAssertionAssert usingRecursiveAssertion() {
        return super.usingRecursiveAssertion();
    }
}
