package org.gridsuite.shortcircuit.server.report;

import com.powsybl.commons.report.TypedValue;
import org.gridsuite.shortcircuit.server.report.mappers.AdnSummarizeMapper;
import org.gridsuite.shortcircuit.server.report.mappers.SeverityMapper;
import org.gridsuite.shortcircuit.server.service.ShortCircuitRunContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Just initialize all the mappers
 */
@Configuration
public class MapperBeans {
    @Bean
    public SeverityMapper powsyblAdnGeneratorsAndBatteriesSeverity() {
        // in generatorConversion and batteryConversion
        return new SeverityMapper("rte.iidm.export.adn.disconnectedTerminalGenerator", TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnLinesSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("rte.iidm.export.adn.lineConversion", "rte.iidm.export.adn.addConstantRatio", TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnTwoWindingsTransformersSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("rte.iidm.export.adn.twoWindingsTransformerConversion", "rte.iidm.export.adn.addConstantRatio", TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnGeneratorsSummary() {
        return new AdnSummarizeMapper("generators",
                "rte.iidm.export.adn.generatorConversion",
                "rte.iidm.export.adn.disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterGenerator);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnBatteriesSummary() {
        return new AdnSummarizeMapper("batteries",
                "rte.iidm.export.adn.batteryConversion",
                "rte.iidm.export.adn.disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterBattery);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnLinesSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("lines",
                "rte.iidm.export.adn.lineConversion",
                "rte.iidm.export.adn.addConstantRatio",
                "shortcircuit.server.addConstantRatioSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterLines);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnTwoWindingsTransformersSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("two windings transformers",
                "rte.iidm.export.adn.twoWindingsTransformerConversion",
                "rte.iidm.export.adn.addConstantRatio",
                "shortcircuit.server.addConstantRatioSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterT2W);
    }

    /* There is also possibly adn nodes:
     * branchConversion.twoWindingsTransformerConversion
     * branchConversion.threeWindingsTransformerConversion
     * branchConversion.lineConversion
     * branchConversion.tieLineConversion
     * danglinglinesConversion
     */
}
