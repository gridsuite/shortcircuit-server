/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.MapAssert;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Assertions methods for {@link Report}.
 * <br/>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(Reporter)}</code>.
 */
public class ReportAssert extends AbstractAssert<ReportAssert, Report> {
    protected ReportAssert(Report actual, Class<? extends ReportAssert> selfType) {
        super(actual, selfType);
    }

    public ReportAssert(Report actual) {
        this(actual, ReportAssert.class);
    }

    public ReportAssert hasNoValues() {
        this.getValues().isEmpty();
        return myself;
    }

    public ReportAssert hasNotValue(TypedValue value) {
        this.getValues().doesNotContainValue(value);
        return myself;
    }

    public ReportAssert hasNotValueKey(String... keys) {
        this.getValues().doesNotContainKeys(keys);
        return myself;
    }

    public ReportAssert hasValue(TypedValue... values) {
        this.getValues().containsValues(values);
        return myself;
    }

    public ReportAssert hasValueKey(String... keys) {
        this.getValues().containsKeys(keys);
        return myself;
    }

    public MapAssert<String, TypedValue> getValues() {
        return this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class));
    }

    public ReportAssert hasReportKeySatisfying(Consumer<String> requirement) {
        this.extracting(Report::getReportKey, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public ReportAssert hasReportKeySatisfying(Condition<? super String> condition) {
        this.extracting(Report::getReportKey, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public ReportAssert hasDefaultMessageSatisfying(Consumer<String> requirement) {
        this.extracting(Report::getDefaultMessage, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public ReportAssert hasDefaultMessageSatisfying(Condition<? super String> condition) {
        this.extracting(Report::getDefaultMessage, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public ReportAssert hasValuesSatisfying(Consumer<Map<String, TypedValue>> requirement) {
        this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class)).satisfies(requirement);
        return myself;
    }

    public ReportAssert hasValuesSatisfying(Condition<? super Map<String, TypedValue>> condition) {
        this.extracting(Report::getValues, InstanceOfAssertFactories.map(String.class, TypedValue.class)).satisfies(condition);
        return myself;
    }
}
