package org.gridsuite.shortcircuit.server.assertj;

import com.google.auto.service.AutoService;
import com.powsybl.commons.reporter.Report;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;

/**
 * A {@code toString()} implementation a little more useful than "{@code com.powsybl.commons.reporter.Report@14998e21}"
 * in assertion messages.
 */
@AutoService(Representation.class)
public class ReportRepresentation extends StandardRepresentation {
    /**
     * {@inheritDoc}
     */
    @Override
    public String toStringOf(Object object) {
        if (object instanceof Report report) {
            return "@" + StringUtils.rightPad(Integer.toHexString(System.identityHashCode(report)), 9)
                    + (report.getValues().containsKey("reportSeverity") ? report.getValue("reportSeverity").getValue() : "UNKOWN")
                    + " [" + report.getReportKey() + "] " + StringSubstitutor.replace(report.getDefaultMessage(), report.getValues());
        } else {
            return super.toStringOf(object);
        }
    }
}
