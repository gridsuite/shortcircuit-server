package org.gridsuite.shortcircuit.server.entities.parameters;

import com.powsybl.shortcircuit.VoltageRange;

import java.util.List;

/**
 * Shared constants for short circuit parameters.
 */
public final class ShortCircuitParametersConstants {
    // This voltage intervals' definition is not clean and we could potentially lose some buses.
    // To be cleaned when VoltageRange uses intervals that are open on the right.
    // TODO: to be moved to RTE private config or to powsybl-rte-core
    public static final List<VoltageRange> CEI909_VOLTAGE_PROFILE = List.of(
            new VoltageRange(0, 199.999, 1.1),
            new VoltageRange(200.0, 299.999, 1.09),
            new VoltageRange(300.0, 389.99, 1.10526),
            new VoltageRange(390.0, 410.0, 1.05)
    );

    public static final String UNIQUE_PROVIDER = "unique_provider";

    private ShortCircuitParametersConstants() {
        // utility class
    }
}
