package org.gridsuite.shortcircuit.server.report;

import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;

public interface ReportMapper {
    /**
     * Look into the node and perform action on it if wanted.
     *
     * @param node the current node visited
     */
    void transformNode(@NonNull final ReportNode node, @NonNull final ShortCircuitRunContext runContext);
}
