package org.gridsuite.shortcircuit.server.report.mappers;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.shortcircuit.server.report.ShortcircuitServerReportResourceBundle;
import org.gridsuite.shortcircuit.server.report.ReportMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.springframework.stereotype.Component;

@Component
public class VoltageLevelsWithWrongIpValuesMapper implements ReportMapper {
    /** {@inheritDoc} */
    @Override
    public void transformNode(@NonNull final ReportNode node, @NonNull final ShortCircuitRunContext runContext) {
        // only add a log line at the (true) root node
        if ("ws.commons.reportType".equals(node.getMessageKey())
                && node.getValue("reportType").map(str -> ((String) str.getValue()).endsWith("ShortCircuitAnalysis")).orElse(Boolean.FALSE)
                && !runContext.getVoltageLevelsWithWrongIsc().isEmpty()) {
            node.newReportNode()
                .withResourceBundles(ShortcircuitServerReportResourceBundle.BASE_NAME) //TODO what is this bug with tests?
                .withMessageTemplate("shortcircuit.server.VoltageLevelsWithWrongIscValues").add()
                .newReportNode()
                .withResourceBundles(ShortcircuitServerReportResourceBundle.BASE_NAME) //TODO what is this bug with tests?
                .withMessageTemplate("shortcircuit.server.VoltageLevelsWithWrongIscValuesSummarize")
                .withUntypedValue("voltageLevels", StringUtils.join(runContext.getVoltageLevelsWithWrongIsc(), ", "))
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withTimestamp()
                .add();
        }
    }
}
