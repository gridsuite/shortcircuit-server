package org.gridsuite.shortcircuit.server.assertj;

import com.powsybl.commons.reporter.ReporterModel;

/**
 * Assertion methods for {@code Reporter}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(ReporterModel)}</code>.
 */
public class ReporterModelAssert extends AbstractReporterModelAssert<ReporterModelAssert> {
    public ReporterModelAssert(ReporterModel actual) {
        super(actual, ReporterModelAssert.class);
    }
}
