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
 * Copyright 2012-2023 the original author or authors.
 */
package org.gridsuite.shortcircuit.server.assertj;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import org.assertj.core.api.InstanceOfAssertFactory;

/**
 * Assertion methods for {@code Reporter}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(Reporter)}</code>.
 */
public class ReporterAssert extends AbstractReporterAssert<ReporterAssert, Reporter> {
    public ReporterAssert(Reporter actual) {
        super(actual, ReporterAssert.class);
    }

    public AbstractReporterModelAssert<?> asInstanceOfReportModel() {
        return this.asInstanceOf(new InstanceOfAssertFactory<>(ReporterModel.class, Assertions::assertThat));
    }
}
