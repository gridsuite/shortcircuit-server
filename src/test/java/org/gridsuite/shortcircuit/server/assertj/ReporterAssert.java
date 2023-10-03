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
