/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.gridsuite.shortcircuit.server.repositories.ParametersRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


import static org.mockito.Mockito.*;

@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShortCircuitServiceTest implements WithAssertions {
    private NotificationService notificationService;
    private UuidGeneratorService uuidGeneratorService;
    private ShortCircuitAnalysisResultService resultService;
    private ComputationS3Service computationS3Service;
    private ParametersRepository parametersRepository;
    private FilterService filterService;
    private ShortCircuitParametersService parametersService;
    private ObjectMapper objectMapper;
    private ShortCircuitService shortCircuitService;

    @BeforeAll
    void setUp() {
        this.notificationService = mock(NotificationService.class);
        this.uuidGeneratorService = spy(new UuidGeneratorService());
        this.resultService = mock(ShortCircuitAnalysisResultService.class);
        this.parametersRepository = mock(ParametersRepository.class);
        this.filterService = mock(FilterService.class);
        this.parametersService = mock(ShortCircuitParametersService.class);
        this.objectMapper = spy(new ObjectMapper());
        this.shortCircuitService = new ShortCircuitService(notificationService, uuidGeneratorService, resultService, computationS3Service, filterService, parametersService, objectMapper);
    }

    @AfterEach
    void checkMocks() {
        try {
            Mockito.verifyNoMoreInteractions(
                notificationService,
                uuidGeneratorService,
                resultService,
                parametersRepository,
                filterService,
                parametersService,
                objectMapper
            );
        } finally {
            Mockito.reset(
                notificationService,
                uuidGeneratorService,
                resultService,
                parametersRepository,
                filterService,
                parametersService,
                objectMapper
            );
        }
    }
}
