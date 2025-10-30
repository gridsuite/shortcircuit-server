package org.gridsuite.shortcircuit.server.report.mappers;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.report.ReportMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;

/**
 * Pass some of the verbose logs to {@link TypedValue#TRACE_SEVERITY TRACE} severity for example.
 */
@Slf4j
@AllArgsConstructor
@Data
public class SeverityMapper implements ReportMapper {
    @Nullable
    private final String parentMessageKey; //"Conversion of ..."
    @NonNull private final String messageKey;
    @NonNull private final TypedValue severity;

    public SeverityMapper(@NonNull String messageKey, @NonNull TypedValue severity) {
        this(null, messageKey, severity);
    }

    /** {@inheritDoc}  */
    @Override
    public void transformNode(final @NonNull ReportNode node, @Nullable final ShortCircuitRunContext unused) {
        if (parentMessageKey == null) {
            this.testAndSet(node);
        } else {
            if (this.parentMessageKey.equals(node.getMessageKey())) {
                log.debug("ADN logs node {} detected, will analyse it...", this.parentMessageKey);
                for (final ReportNode child : node.getChildren()) {
                    this.testAndSet(child);
                }
            }
        }
    }

    private void testAndSet(final @NonNull ReportNode node) {
        if (this.messageKey.equals(node.getMessageKey())) {
            node.addSeverity(this.severity);
        }
    }
}
