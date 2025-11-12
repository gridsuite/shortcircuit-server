/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.entities.parameters;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import org.gridsuite.shortcircuit.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.shortcircuit.server.dto.ShortCircuitPredefinedConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @Column(name = "withFeederResult", nullable = false, columnDefinition = "boolean default false")
    private boolean withFeederResult = false;

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

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "short_circuit_parameters_id", foreignKey = @ForeignKey(name = "shortCircuitParametersEntity_specificParameters_fk"))
    private List<ShortCircuitSpecificParameterEntity> specificParameters = new ArrayList<>();

    public ShortCircuitParametersEntity(ShortCircuitParametersInfos shortCircuitParametersInfos) {
        assignAttributes(shortCircuitParametersInfos);
    }

    public void update(@NonNull ShortCircuitParametersInfos shortCircuitParametersInfos) {
        assignAttributes(shortCircuitParametersInfos);
    }

    private void assignAttributes(ShortCircuitParametersInfos shortCircuitParametersInfos) {
        ShortCircuitParameters allCommonValues;
        List<ShortCircuitSpecificParameterEntity> allSpecificValuesEntities = new ArrayList<>(List.of());
        if (shortCircuitParametersInfos == null) {
            allCommonValues = ShortCircuitParameters.load();
        } else {
            predefinedParameters = shortCircuitParametersInfos.predefinedParameters();
            allCommonValues = shortCircuitParametersInfos.commonParameters();
            Map<String, Map<String, String>> specificParametersPerProvider = shortCircuitParametersInfos.specificParametersPerProvider();
            if (specificParametersPerProvider != null) {
                specificParametersPerProvider.forEach((p, paramsMap) -> {
                    if (paramsMap != null) {
                        paramsMap.forEach((paramName, paramValue) -> {
                            if (paramValue != null) {
                                allSpecificValuesEntities.add(new ShortCircuitSpecificParameterEntity(
                                        null,
                                        p,
                                        paramName,
                                        paramValue));
                            }
                        });
                    }
                });
            }
        }
        assignCommonValues(allCommonValues);
        assignSpecificValues(allSpecificValuesEntities);
    }

    private void assignCommonValues(ShortCircuitParameters allCommonValues) {
        withLimitViolations = allCommonValues.isWithLimitViolations();
        withVoltageResult = allCommonValues.isWithVoltageResult();
        withFeederResult = allCommonValues.isWithFeederResult();
        studyType = allCommonValues.getStudyType();
        minVoltageDropProportionalThreshold = allCommonValues.getMinVoltageDropProportionalThreshold();
        withLoads = allCommonValues.isWithLoads();
        withShuntCompensators = allCommonValues.isWithShuntCompensators();
        withVscConverterStations = allCommonValues.isWithVSCConverterStations();
        withNeutralPosition = allCommonValues.isWithNeutralPosition();
        initialVoltageProfileMode = allCommonValues.getInitialVoltageProfileMode();
    }

    private void assignSpecificValues(List<ShortCircuitSpecificParameterEntity> allSpecificValuesEntities) {
        if (specificParameters == null) {
            specificParameters = allSpecificValuesEntities;
        } else {
            specificParameters.clear();
            if (!allSpecificValuesEntities.isEmpty()) {
                specificParameters.addAll(allSpecificValuesEntities);
            }
        }
    }

    public ShortCircuitParameters toShortCircuitParameters() {
        return ShortCircuitParameters.load()
                .setWithLimitViolations(withLimitViolations)
                .setWithVoltageResult(withVoltageResult)
                .setWithFeederResult(withFeederResult)
                .setStudyType(studyType)
                .setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold)
                .setWithLoads(withLoads)
                .setWithShuntCompensators(withShuntCompensators)
                .setWithVSCConverterStations(withVscConverterStations)
                .setWithNeutralPosition(withNeutralPosition)
                .setInitialVoltageProfileMode(initialVoltageProfileMode)
                // the voltageRanges is not taken into account when initialVoltageProfileMode=NOMINAL
                .setVoltageRanges(InitialVoltageProfileMode.CONFIGURED.equals(initialVoltageProfileMode) ? ShortCircuitParametersConstants.CEI909_VOLTAGE_PROFILE : null);
    }
}
