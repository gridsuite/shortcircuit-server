/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.test.assertj;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;

import java.util.function.Consumer;

/**
 * Assertion methods for {@link ReporterModel}.
 * <br/>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(ReporterModel)}</code>.
 */
public class ReporterModelAssert extends AbstractReporterAssert<ReporterModelAssert, ReporterModel> {
    protected ReporterModelAssert(ReporterModel actual, Class<? extends ReporterModelAssert> selfType) {
        super(actual, selfType);
    }

    public ReporterModelAssert(ReporterModel actual) {
        this(actual, ReporterModelAssert.class);
    }

    public ReporterModelAssert hasNoReports() {
        this.getReports().isEmpty();
        return myself;
    }

    public ListAssert<Report> getReports() {
        return this.extracting(ReporterModel::getReports, InstanceOfAssertFactories.list(Report.class));
    }

    public ReporterModelAssert hasSubReporters() {
        this.getSubReporters().isEmpty();
        return myself;
    }

    public ListAssert<ReporterModel> getSubReporters() {
        return this.extracting(ReporterModel::getSubReporters, InstanceOfAssertFactories.list(ReporterModel.class));
    }

    public ReporterModelAssert hasTaskKeySatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getTaskKey, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public ReporterModelAssert hasTaskKeySatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getTaskKey, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public ReporterModelAssert hasDefaultNameSatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getDefaultName, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public ReporterModelAssert hasDefaultNameSatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getDefaultName, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public ReporterModelAssert hasTaskValuesSatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getTaskValues, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public ReporterModelAssert hasTaskValuesSatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getTaskValues, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }
}
