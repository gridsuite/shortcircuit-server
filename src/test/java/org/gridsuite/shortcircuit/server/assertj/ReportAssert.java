package org.gridsuite.shortcircuit.server.assertj;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;

/**
 * Assertion methods for {@code Report}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link Assertions#assertThat(Reporter)}</code>.
 */
public class ReportAssert extends AbstractReportAssert<ReportAssert> {
    public ReportAssert(Report actual) {
        super(actual, ReportAssert.class);
    }
}
