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

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.TypedValue;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.MapAssert;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Assertions for {@link Report}.
 *
 * @param <SELF> the "self" type of this assertion class.
 */
public abstract class AbstractReportAssert<SELF extends AbstractReportAssert<SELF>> extends AbstractAssert<SELF, Report> {
    protected AbstractReportAssert(Report actual, Class<? extends AbstractReportAssert> selfType) {
        super(actual, selfType);
    }

    public SELF hasNoValues() {
        this.getValues().isEmpty();
        return myself;
    }

    public SELF hasNotValue(TypedValue value) {
        this.getValues().doesNotContainValue(value);
        return myself;
    }

    public SELF hasNotValueKey(String... keys) {
        this.getValues().doesNotContainKeys(keys);
        return myself;
    }

    public SELF hasValue(TypedValue... values) {
        this.getValues().containsValues(values);
        return myself;
    }

    public SELF hasValueKey(String... keys) {
        this.getValues().containsKeys(keys);
        return myself;
    }

    public MapAssert<String, TypedValue> getValues() {
        return this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class));
    }

    void a() {
        actual.getValues();
        actual.getValue(null);
    }

    public SELF hasReportKeySatisfying(Consumer<String> requirement) {
        this.extracting(Report::getReportKey, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public SELF hasReportKeySatisfying(Condition<? super String> condition) {
        this.extracting(Report::getReportKey, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public SELF hasDefaultMessageSatisfying(Consumer<String> requirement) {
        this.extracting(Report::getDefaultMessage, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public SELF hasDefaultMessageSatisfying(Condition<? super String> condition) {
        this.extracting(Report::getDefaultMessage, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public SELF hasValuesSatisfying(Consumer<Map<String, TypedValue>> requirement) {
        this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class)).satisfies(requirement);
        return myself;
    }

    public SELF hasValuesSatisfying(Condition<? super Map<String, TypedValue>> condition) {
        this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class)).satisfies(condition);
        return myself;
    }
}
