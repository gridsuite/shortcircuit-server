/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "shortCircuitParameters")
public class ShortCircuitParametersEntity {
    public ShortCircuitParametersEntity(boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult,
                                        boolean withFeederResult, StudyType studyType, double minVoltageDropProportionalThreshold,
                                        ShortCircuitPredefinedConfiguration predefinedParameters, boolean withLoads,
                                        boolean withShuntCompensators, boolean withVscConverterStations,
                                        boolean withNeutralPosition, InitialVoltageProfileMode initialVoltageProfileMode) {
        this(null, withLimitViolations, withVoltageResult, withFortescueResult, withFeederResult, studyType, minVoltageDropProportionalThreshold, predefinedParameters, withLoads, withShuntCompensators, withVscConverterStations, withNeutralPosition, initialVoltageProfileMode);
    }

    public ShortCircuitParametersEntity(ShortCircuitParametersEntity entity) {
        this();
        this.updateFrom(entity);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "withLimitViolations", columnDefinition = "boolean default true")
    private boolean withLimitViolations = true;

    @Column(name = "withVoltageResult", columnDefinition = "boolean default false")
    private boolean withVoltageResult = false;

    @Column(name = "withFortescueResult", columnDefinition = "boolean default false")
    private boolean withFortescueResult = false;

    @Column(name = "withFeederResult", columnDefinition = "boolean default true")
    private boolean withFeederResult = true;

    @Column(name = "studyType", columnDefinition = "varchar(15) default \"TRANSIENT\"")
    @Enumerated(EnumType.STRING)
    private StudyType studyType = StudyType.TRANSIENT;

    @Column(name = "minVoltageDropProportionalThreshold", columnDefinition = "double precision default '20.0'")
    private double minVoltageDropProportionalThreshold = 20.0;

    @Column(name = "predefinedParameters", columnDefinition = "varchar(35) default \"ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP\"")
    @Enumerated(EnumType.STRING)
    private ShortCircuitPredefinedConfiguration predefinedParameters = ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP;

    @Column(name = "withLoads", columnDefinition = "boolean default false")
    private boolean withLoads = false;

    @Column(name = "withShuntCompensators", columnDefinition = "boolean default false")
    private boolean withShuntCompensators = false;

    @Column(name = "withVscConverterStations", columnDefinition = "boolean default true")
    private boolean withVscConverterStations = true;

    @Column(name = "withNeutralPosition", columnDefinition = "boolean default true")
    private boolean withNeutralPosition = true;

    @Column(name = "initialVoltageProfileMode", columnDefinition = "varchar(15) default \"NOMINAL\"")
    @Enumerated(EnumType.STRING)
    private InitialVoltageProfileMode initialVoltageProfileMode = InitialVoltageProfileMode.NOMINAL;

    /**
     * Reset values to defaults ones
     * @return this instance
     */
    public ShortCircuitParametersEntity resetToDefaults() {
        this.updateFrom(new ShortCircuitParametersEntity());
        return this;
    }

    /**
     * Update an entity using values of another one
     * @param entity the entity to take values from, will reset if {@code null}
     * @return this instance
     */
    public ShortCircuitParametersEntity updateFrom(@Nullable final ShortCircuitParametersEntity entity) {
        if (entity == null) {
            this.resetToDefaults();
        } else {
            this.setWithLimitViolations(entity.withLimitViolations);
            this.setWithVoltageResult(entity.withVoltageResult);
            this.setWithFortescueResult(entity.withFortescueResult);
            this.setWithFeederResult(entity.withFeederResult);
            this.setStudyType(entity.studyType);
            this.setMinVoltageDropProportionalThreshold(entity.minVoltageDropProportionalThreshold);
            this.setPredefinedParameters(entity.predefinedParameters);
            this.setWithLoads(entity.withLoads);
            this.setWithShuntCompensators(entity.withShuntCompensators);
            this.setWithVscConverterStations(entity.withVscConverterStations);
            this.setWithNeutralPosition(entity.withNeutralPosition);
            this.setInitialVoltageProfileMode(entity.initialVoltageProfileMode);
        }
        return this;
    }
}
