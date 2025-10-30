package org.gridsuite.shortcircuit.server.report;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeImpl;
import com.powsybl.commons.report.ReportNodeNoOp;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ReportMapperService {
    @NonNull private final List<ReportMapper> mappers;

    /**
     * Do actions on a tree of {@link ReportMapper}s.
     *
     * @param rootReportNode The tree to transform.
     * @param runContext The run context used
     * @return The transformed tree (same instance).
     * @implNote We don't support a tree with a big depth because we use a recursive walker internally.
     * @apiNote Because {@link ReportNode} doesn't define setters, and known {@link ReportNodeImpl} & {@link ReportNodeNoOp} return
     *     either {@code Collections.unmodifiable*()} or {@code Collections.empty*()}, no modification of {@code messageKey} and
     *     deletion of node is supported.
     */
    public ReportNode map(@Nullable final ReportNode rootReportNode, @NonNull final ShortCircuitRunContext runContext) {
        /* Quick check to be sure it's a tree build from AbstractWorkerService#run(...) */
        if (rootReportNode == null) {
            log.debug("No logs report, nothing to do.");
        } else if (!"ws.commons.rootReporterId".equals(rootReportNode.getMessageKey())) {
            log.debug("Unrecognized ReportNode: {}", rootReportNode);
        } else if (mappers.isEmpty()) {
            log.debug("No mapper to apply, nothing to do.");
        } else {
            log.info("ShortCircuitAnalysis root node: will modify it!");
            this.recursiveMap(rootReportNode, runContext);
        }
        return rootReportNode;
    }

    //TODO redo the function in non-recursive one to avoid possibly StackOverflowError
    private void recursiveMap(@NonNull final ReportNode reportNode, @NonNull final ShortCircuitRunContext runContext) {
        for (final ReportMapper mapper : mappers) {
            mapper.transformNode(reportNode, runContext);
        }
        for (final ReportNode childNode : reportNode.getChildren()) {
            recursiveMap(childNode, runContext);
        }
    }
}
