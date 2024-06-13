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
public class AnalysisParametersEntity {

    public AnalysisParametersEntity(boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult, boolean withFeederResult,
                                    StudyType studyType, double minVoltageDropProportionalThreshold, ShortCircuitPredefinedConfiguration predefinedParameters,
                                    boolean withLoads, boolean withShuntCompensators, boolean withVscConverterStations, boolean withNeutralPosition,
                                    InitialVoltageProfileMode initialVoltageProfileMode) {
        this(null, withLimitViolations, withVoltageResult, withFortescueResult, withFeederResult, studyType, minVoltageDropProportionalThreshold,
                predefinedParameters, withLoads, withShuntCompensators, withVscConverterStations, withNeutralPosition, initialVoltageProfileMode);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Builder.Default
    @Column(name = "withLimitViolations", columnDefinition = "boolean default true")
    private boolean withLimitViolations = true;

    @Builder.Default
    @Column(name = "withVoltageResult", columnDefinition = "boolean default false")
    private boolean withVoltageResult = false;

    @Builder.Default
    @Column(name = "withFortescueResult", columnDefinition = "boolean default false")
    private boolean withFortescueResult = false;

    @Builder.Default
    @Column(name = "withFeederResult", columnDefinition = "boolean default true")
    private boolean withFeederResult = true;

    @Builder.Default
    @Column(name = "studyType", columnDefinition = "varchar(255) default 'TRANSIENT'")
    @Enumerated(EnumType.STRING)
    private StudyType studyType = StudyType.TRANSIENT;

    @Builder.Default
    @Column(name = "minVoltageDropProportionalThreshold", columnDefinition = "double precision default 20.0")
    private double minVoltageDropProportionalThreshold = 20.0;

    @Builder.Default
    @Column(name = "predefinedParameters", columnDefinition = "varchar(255) default 'ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP'")
    @Enumerated(EnumType.STRING)
    private ShortCircuitPredefinedConfiguration predefinedParameters = ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP;

    @Builder.Default
    @Column(name = "withLoads", columnDefinition = "boolean default false")
    private boolean withLoads = false;

    @Builder.Default
    @Column(name = "withShuntCompensators", columnDefinition = "boolean default false")
    private boolean withShuntCompensators = false;

    @Builder.Default
    @Column(name = "withVscConverterStations", columnDefinition = "boolean default true")
    private boolean withVscConverterStations = true;

    @Builder.Default
    @Column(name = "withNeutralPosition", columnDefinition = "boolean default true")
    private boolean withNeutralPosition = true;

    @Builder.Default
    @Column(name = "initialVoltageProfileMode", columnDefinition = "varchar(255) default 'NOMINAL'")
    @Enumerated(EnumType.STRING)
    private InitialVoltageProfileMode initialVoltageProfileMode = InitialVoltageProfileMode.NOMINAL;
}