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
import com.powsybl.commons.reporter.ReporterModel;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;

import java.util.function.Consumer;

/**
 * Assertions for {@link ReporterModel}.
 *
 * @param <SELF> the "self" type of this assertion class.
 */
public abstract class AbstractReporterModelAssert<SELF extends AbstractReporterModelAssert<SELF>> extends AbstractReporterAssert<SELF, ReporterModel> {

    protected AbstractReporterModelAssert(ReporterModel actual, Class<? extends SELF> selfType) {
        super(actual, selfType);
    }

    public SELF hasNoReports() {
        this.getReports().isEmpty();
        return myself;
    }

    public ListAssert<Report> getReports() {
        return this.extracting(ReporterModel::getReports, InstanceOfAssertFactories.list(Report.class));
    }

    public SELF hasSubReporters() {
        this.getSubReporters().isEmpty();
        return myself;
    }

    public ListAssert<ReporterModel> getSubReporters() {
        return this.extracting(ReporterModel::getSubReporters, InstanceOfAssertFactories.list(ReporterModel.class));
    }

    public SELF hasTaskKeySatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getTaskKey, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public SELF hasTaskKeySatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getTaskKey, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public SELF hasDefaultNameSatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getDefaultName, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public SELF hasDefaultNameSatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getDefaultName, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }

    public SELF hasTaskValuesSatisfying(Consumer<String> requirement) {
        this.extracting(ReporterModel::getTaskValues, InstanceOfAssertFactories.STRING).satisfies(requirement);
        return myself;
    }

    public SELF hasTaskValuesSatisfying(Condition<? super String> condition) {
        this.extracting(ReporterModel::getTaskValues, InstanceOfAssertFactories.STRING).satisfies(condition);
        return myself;
    }
}
