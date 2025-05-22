package org.gridsuite.shortcircuit.server.report.mappers;

import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.report.ShortcircuitServerReportResourceBundle;
import org.gridsuite.shortcircuit.server.report.ReportMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pass some of the verbose ADN logs to {@link TypedValue#DETAIL_SEVERITY DETAIL} severity and insert a summarized log line.
 *
 * @see com.rte_france.powsybl.iidm.export.adn.ADNHelper
 * @see com.rte_france.powsybl.iidm.export.adn.BranchHelper
 */
@Slf4j
@Data
public class AdnTraceLevelAndSummarizeMapper implements ReportMapper {
    @NonNull public final String equipmentsLabel;
    @NonNull public final String parentMessageKey; //"Conversion of ..."
    @NonNull public final String toSummarizeMessageKey;
    @NonNull public final String summaryMessageKey;
    private long logsToSummarizeCount; // =0L
    private TypedValue logsToSummarizeSeverity = TypedValue.WARN_SEVERITY;

    /** {@inheritDoc}  */
    @Override
    public void transformNode(final @NonNull ReportNode node, @Nullable final ShortCircuitRunContext unused) {
        if (this.parentMessageKey.equals(node.getMessageKey())) {
            log.debug("ADN logs node for {} detected, will analyse them...", this.equipmentsLabel);
            for (final ReportNode child : node.getChildren()) {
                if (this.toSummarizeMessageKey.equals(child.getMessageKey())) {
                    child.getValue(ReportConstants.SEVERITY_KEY).ifPresent(severity -> logsToSummarizeSeverity = severity);
                    child.addSeverity(TypedValue.DETAIL_SEVERITY);
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
                    .withSeverity(this.logsToSummarizeSeverity)
                    .withUntypedValue("equipmentsLabel", this.equipmentsLabel)
                    .withUntypedValue("nb", this.logsToSummarizeCount)
                    .add();
            }
        }
    }

    /**
     * Just initialize all the mappers
     */
    @Configuration
    public static class AdnMapperBeans {

        @Bean
        public AdnTraceLevelAndSummarizeMapper powsyblAdnGeneratorsMapper() {
            return new AdnTraceLevelAndSummarizeMapper("generators",
                "generatorConversion",
                "disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary");
        }

        @Bean
        public AdnTraceLevelAndSummarizeMapper powsyblAdnBatteriesMapper() {
            return new AdnTraceLevelAndSummarizeMapper("batteries",
                "batteryConversion",
                "disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary");
        }

        @Bean
        public AdnTraceLevelAndSummarizeMapper powsyblAdnTwoWindingsTransformersMapper() {
            // in branchConversion.twoWindingsTransformerConversion
            return new AdnTraceLevelAndSummarizeMapper("two windings transformers",
                "twoWindingsTransformerConversion",
                "addConstantRatio",
                "shortcircuit.server.addConstantRatioSummary");
        }

        /* There is also possibly nodes:
         * branchConversion.threeWindingsTransformerConversion
         * branchConversion.lineConversion
         * branchConversion.tieLineConversion
         * danglinglinesConversion
         */
    }
}
