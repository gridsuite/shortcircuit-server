/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;

import java.util.UUID;

/**
 * @since 1.7.0
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
@Entity
@Table(name = "shortcircuit_parameters")
public class ShortCircuitParametersEntity {

    public ShortCircuitParametersEntity(boolean withLimitViolations, boolean withVoltageResult, boolean withFeederResult, StudyType studyType,
                                        double minVoltageDropProportionalThreshold, ShortCircuitPredefinedConfiguration predefinedParameters,
                                        boolean withLoads, boolean withShuntCompensators, boolean withVscConverterStations, boolean withNeutralPosition,
                                        InitialVoltageProfileMode initialVoltageProfileMode) {
        this(null, withLimitViolations, withVoltageResult, withFeederResult, studyType, minVoltageDropProportionalThreshold,
                predefinedParameters, withLoads, withShuntCompensators, withVscConverterStations, withNeutralPosition, initialVoltageProfileMode);
    }

    public ShortCircuitParametersEntity(@NonNull final ShortCircuitParametersEntity sourceToClone) {
        this(sourceToClone.isWithLimitViolations(),
            sourceToClone.isWithVoltageResult(),
            sourceToClone.isWithFeederResult(),
            sourceToClone.getStudyType(),
            sourceToClone.getMinVoltageDropProportionalThreshold(),
            sourceToClone.getPredefinedParameters(),
            sourceToClone.isWithLoads(),
            sourceToClone.isWithShuntCompensators(),
            sourceToClone.isWithVscConverterStations(),
            sourceToClone.isWithNeutralPosition(),
            sourceToClone.getInitialVoltageProfileMode());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Builder.Default
    @Column(name = "withLimitViolations", nullable = false, columnDefinition = "boolean default true")
    private boolean withLimitViolations = true;

    @Builder.Default
    @Column(name = "withVoltageResult", nullable = false, columnDefinition = "boolean default false")
    private boolean withVoltageResult = false;

    @Builder.Default
    @Column(name = "withFeederResult", nullable = false, columnDefinition = "boolean default true")
    private boolean withFeederResult = true;

    @Builder.Default
    @Column(name = "studyType", columnDefinition = "varchar(255) default 'TRANSIENT'")
    @Enumerated(EnumType.STRING)
    private StudyType studyType = StudyType.TRANSIENT;

    @Builder.Default
    @Column(name = "minVoltageDropProportionalThreshold", nullable = false, columnDefinition = "double precision default 20.0")
    private double minVoltageDropProportionalThreshold = 20.0;

    @Builder.Default
    @Column(name = "predefinedParameters", columnDefinition = "varchar(255) default 'ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP'")
    @Enumerated(EnumType.STRING)
    private ShortCircuitPredefinedConfiguration predefinedParameters = ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP;

    @Builder.Default
    @Column(name = "withLoads", nullable = false, columnDefinition = "boolean default false")
    private boolean withLoads = false;

    @Builder.Default
    @Column(name = "withShuntCompensators", nullable = false, columnDefinition = "boolean default false")
    private boolean withShuntCompensators = false;

    @Builder.Default
    @Column(name = "withVscConverterStations", nullable = false, columnDefinition = "boolean default true")
    private boolean withVscConverterStations = true;

    @Builder.Default
    @Column(name = "withNeutralPosition", nullable = false, columnDefinition = "boolean default true")
    private boolean withNeutralPosition = true;

    @Builder.Default
    @Column(name = "initialVoltageProfileMode", columnDefinition = "varchar(255) default 'NOMINAL'")
    @Enumerated(EnumType.STRING)
    private InitialVoltageProfileMode initialVoltageProfileMode = InitialVoltageProfileMode.NOMINAL;

    public ShortCircuitParametersEntity updateWith(final ShortCircuitParametersEntity source) {
        return this.setWithLimitViolations(source.isWithLimitViolations())
                   .setWithVoltageResult(source.isWithVoltageResult())
                   .setWithFeederResult(source.isWithFeederResult())
                   .setStudyType(source.getStudyType())
                   .setMinVoltageDropProportionalThreshold(source.getMinVoltageDropProportionalThreshold())
                   .setPredefinedParameters(source.getPredefinedParameters())
                   .setWithLoads(source.isWithLoads())
                   .setWithShuntCompensators(source.isWithShuntCompensators())
                   .setWithVscConverterStations(source.isWithVscConverterStations())
                   .setWithNeutralPosition(source.isWithNeutralPosition())
                   .setInitialVoltageProfileMode(source.getInitialVoltageProfileMode());
    }
}
