package org.gridsuite.shortcircuit.server.dto;

import org.springframework.data.domain.Page;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
public record ShortCircuitAnalysisPagedResultsAllBuses(Page<FaultResult> page) implements ShortCircuitAnalysisPagedResults {

}
