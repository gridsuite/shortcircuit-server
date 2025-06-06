package org.gridsuite.shortcircuit.server.report.mappers;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.report.ReportMapper;
import org.gridsuite.shortcircuit.server.report.ShortcircuitServerReportResourceBundle;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.jetbrains.annotations.Nullable;

/**
 * Insert a summarized log line for some of the verbose ADN logs.
 *
 * @see com.rte_france.powsybl.iidm.export.adn.ADNHelper
 * @see com.rte_france.powsybl.iidm.export.adn.BranchHelper
 */
@Slf4j
@Data
public class AdnSummarizeMapper implements ReportMapper {
    @NonNull private final String equipmentsLabel;
    @NonNull private final String parentMessageKey; //"Conversion of ..."
    @NonNull private final String toSummarizeMessageKey;
    @NonNull private final String summaryMessageKey;
    private long logsToSummarizeCount; // =0L

    /** {@inheritDoc}  */
    @Override
    public void transformNode(final @NonNull ReportNode node, @Nullable final ShortCircuitRunContext unused) {
        if (this.parentMessageKey.equals(node.getMessageKey())) {
            log.debug("ADN logs node for {} detected, will analyse them...", this.equipmentsLabel);
            for (final ReportNode child : node.getChildren()) {
                if (this.toSummarizeMessageKey.equals(child.getMessageKey())) {
                    this.logsToSummarizeCount++;
                }
            }
            /* finalize computation of summaries */
            log.debug("Found {} lines in shortcircuit logs matching {}", this.logsToSummarizeCount, this.toSummarizeMessageKey);
            if (this.logsToSummarizeCount > 0L) {
                node.newReportNode()
                    .withResourceBundles(ShortcircuitServerReportResourceBundle.BASE_NAME) //TODO what is this bug with tests?
                    .withMessageTemplate(this.summaryMessageKey)
                    .withTimestamp()
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .withUntypedValue("equipmentsLabel", this.equipmentsLabel)
                    .withUntypedValue("nb", this.logsToSummarizeCount)
                    .add();
            }
        }
    }
}
