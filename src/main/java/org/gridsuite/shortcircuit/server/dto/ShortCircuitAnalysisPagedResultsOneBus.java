package org.gridsuite.shortcircuit.server.dto;

import org.springframework.data.domain.Page;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public record ShortCircuitAnalysisPagedResultsOneBus(FaultResult faultResult, Page<FeederResult> page) implements ShortCircuitAnalysisPagedResults {

}
