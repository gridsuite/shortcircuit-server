package org.gridsuite.shortcircuit.server.report.mappers;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.report.ReportMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.jetbrains.annotations.Nullable;

/**
 * Pass some of the verbose logs to {@link TypedValue#TRACE_SEVERITY TRACE} severity for example.
 */
@Slf4j
@Data
public class SeverityMapper implements ReportMapper {
    @NonNull private final String messageKey;
    @NonNull private final TypedValue severity;

    /** {@inheritDoc}  */
    @Override
    public void transformNode(final @NonNull ReportNode node, @Nullable final ShortCircuitRunContext unused) {
        if (this.messageKey.equals(node.getMessageKey())) {
            node.addSeverity(this.severity);
        }
    }
}
