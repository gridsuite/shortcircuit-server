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

    private static final String DISCONNECTED_TERMINAL_GENERATOR_MESSAGE_KEY = "rte.iidm.export.adn.disconnectedTerminalGenerator";
    private static final String ADD_CONSTANT_RATIO_MESSAGE_KEY = "rte.iidm.export.adn.addConstantRatio";

    @Bean
    public SeverityMapper powsyblAdnGeneratorsAndBatteriesSeverity() {
        // in generatorConversion and batteryConversion
        return new SeverityMapper(DISCONNECTED_TERMINAL_GENERATOR_MESSAGE_KEY, TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnLinesSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("rte.iidm.export.adn.lineConversion", ADD_CONSTANT_RATIO_MESSAGE_KEY, TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnTwoWindingsTransformersSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("rte.iidm.export.adn.twoWindingsTransformerConversion", ADD_CONSTANT_RATIO_MESSAGE_KEY, TypedValue.TRACE_SEVERITY);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnGeneratorsSummary() {
        return new AdnSummarizeMapper("generators",
                "rte.iidm.export.adn.generatorConversion",
                DISCONNECTED_TERMINAL_GENERATOR_MESSAGE_KEY,
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterGenerator);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnBatteriesSummary() {
        return new AdnSummarizeMapper("batteries",
                "rte.iidm.export.adn.batteryConversion",
                DISCONNECTED_TERMINAL_GENERATOR_MESSAGE_KEY,
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterBattery);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnLinesSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("lines",
                "rte.iidm.export.adn.lineConversion",
                ADD_CONSTANT_RATIO_MESSAGE_KEY,
                "shortcircuit.server.addConstantRatioSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterLines);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnTwoWindingsTransformersSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("two windings transformers",
                "rte.iidm.export.adn.twoWindingsTransformerConversion",
                ADD_CONSTANT_RATIO_MESSAGE_KEY,
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
