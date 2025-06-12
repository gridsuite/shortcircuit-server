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
        return new SeverityMapper("disconnectedTerminalGenerator", TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnTwoWindingsTransformersSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("twoWindingsTransformerConversion", "addConstantRatio", TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnGeneratorsSummary() {
        return new AdnSummarizeMapper("generators",
                "generatorConversion",
                "disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterGenerator);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnBatteriesSummary() {
        return new AdnSummarizeMapper("batteries",
                "batteryConversion",
                "disconnectedTerminalGenerator",
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterBattery);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnTwoWindingsTransformersSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("two windings transformers",
                "twoWindingsTransformerConversion",
                "addConstantRatio",
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
