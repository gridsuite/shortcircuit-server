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
    public static final String KEY_DISCONNECTED_GENERATOR = "disconnectedTerminalGenerator";
    public static final String KEY_ADD_CONSTANT_RATION = "addConstantRatio";

    @Bean
    public SeverityMapper powsyblAdnGeneratorsAndBatteriesSeverity() {
        // in generatorConversion and batteryConversion
        return new SeverityMapper(KEY_DISCONNECTED_GENERATOR, TypedValue.DETAIL_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnLinesSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("lineConversion", KEY_ADD_CONSTANT_RATION, TypedValue.DETAIL_SEVERITY);
    }

    @Bean
    public SeverityMapper powsyblAdnTwoWindingsTransformersSeverity() {
        // in branchConversion.twoWindingsTransformerConversion
        return new SeverityMapper("twoWindingsTransformerConversion", KEY_ADD_CONSTANT_RATION, TypedValue.DETAIL_SEVERITY);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnGeneratorsSummary() {
        return new AdnSummarizeMapper("generators",
                "generatorConversion",
                KEY_DISCONNECTED_GENERATOR,
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterGenerator);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnBatteriesSummary() {
        return new AdnSummarizeMapper("batteries",
                "batteryConversion",
                KEY_DISCONNECTED_GENERATOR,
                "shortcircuit.server.disconnectedTerminalEquipmentSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterBattery);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnLinesSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("lines",
                "lineConversion",
                KEY_ADD_CONSTANT_RATION,
                "shortcircuit.server.addConstantRatioSummary",
                ShortCircuitRunContext::getAdnSummarizeCounterLines);
    }

    @Bean
    public AdnSummarizeMapper powsyblAdnTwoWindingsTransformersSummary() {
        // in branchConversion.twoWindingsTransformerConversion
        return new AdnSummarizeMapper("two windings transformers",
                "twoWindingsTransformerConversion",
                KEY_ADD_CONSTANT_RATION,
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
