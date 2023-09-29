/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright 2012-2018 the original author or authors.
 */
package org.gridsuite.shortcircuit.server.assertj;

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
