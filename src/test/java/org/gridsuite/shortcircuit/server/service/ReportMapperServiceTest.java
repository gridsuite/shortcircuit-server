/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.google.auto.service.AutoService;
import com.powsybl.commons.reporter.Reporter;
import lombok.NonNull;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.gridsuite.shortcircuit.server.test.assertj.WithCustomAssertions;
import org.gridsuite.shortcircuit.server.service.reports.ReportMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {ReportMapperServiceTest.SpringConfiguration.class, ReportMapperService.class})
class ReportMapperServiceTest implements WithCustomAssertions {
    private static final String REPORT_MAPPER_SERVICELOADER_NAME = "ReportMapper ServiceLoader example";
    private static final String REPORT_MAPPER_SPRING_BEAN_NAME = "Report mapper spring bean example";

    @Autowired private ReportMapperService mapperService;

    @Test
    void testFindJavaServices() {
        assertThat(mapperService).as("ReportMapperService")
                .extracting(ReportMapperService::getReportMappers, InstanceOfAssertFactories.collection(ReportMapper.class)).as("mapper implementations")
                .hasAtLeastOneElementOfType(ReportMapperSL.class)
                .haveAtLeastOne(new Condition<>(rm -> REPORT_MAPPER_SERVICELOADER_NAME.equals(rm.getName()), "is a ReportMapperSL"));
    }

    @Test
    void testFindSpringBeans() {
        assertThat(mapperService).as("ReportMapperService")
                .extracting(ReportMapperService::getReportMappers, InstanceOfAssertFactories.collection(ReportMapper.class)).as("mapper implementations")
                .haveAtLeastOne(new Condition<>(rm -> REPORT_MAPPER_SPRING_BEAN_NAME.equals(rm.getName()), "is a ReportMapperBean"));
    }

    @Disabled("implementing spring.factories would cause problems in this module")
    @Test
    void testFindSpringServices() {
        assertThat(false).withFailMessage("not implemented").isTrue();
    }

    @Test
    void testDedupMultipleInstances() {
        assertThat(mapperService).as("ReportMapperService")
                .extracting(ReportMapperService::getReportMappers, InstanceOfAssertFactories.collection(ReportMapper.class)).as("mapper implementations")
                .filteredOn(rm -> REPORT_MAPPER_SPRING_BEAN_NAME.equals(rm.getName()))
                .singleElement()
                .extracting(ReportMapper::getVersion, InstanceOfAssertFactories.INTEGER)
                .isOne();
    }

    @Test
    void testKeepMostRecentVersion() {
        assertThat(mapperService).as("ReportMapperService")
                .extracting(ReportMapperService::getReportMappers, InstanceOfAssertFactories.collection(ReportMapper.class)).as("mapper implementations")
                .filteredOn(rm -> REPORT_MAPPER_SERVICELOADER_NAME.equals(rm.getName()))
                .singleElement()
                .isExactlyInstanceOf(ReportMapperSL2.class);
    }

    @AutoService(ReportMapper.class)
    public static class ReportMapperSL implements ReportMapper {
        @Override
        public Reporter mapReporter(@NonNull Reporter reporter) {
            return reporter;
        }

        @Override
        public String getName() {
            return REPORT_MAPPER_SERVICELOADER_NAME;
        }
    }

    @AutoService(ReportMapper.class)
    public static class ReportMapperSL2 extends ReportMapperSL {
        @Override
        public int getVersion() {
            return 2;
        }
    }

    @TestConfiguration
    public static class SpringConfiguration {
        @Bean
        public ReportMapper springTestReportMapper() {
            return new ReportMapper() {
                @Override
                public Reporter mapReporter(@NonNull Reporter reporter) {
                    return reporter;
                }

                @Override
                public String getName() {
                    return REPORT_MAPPER_SPRING_BEAN_NAME;
                }
            };
        }

        @Bean
        public ReportMapper springTestReportMapperDuplicate() {
            return new ReportMapper() {
                @Override
                public Reporter mapReporter(@NonNull Reporter reporter) {
                    return reporter;
                }

                @Override
                public String getName() {
                    return REPORT_MAPPER_SPRING_BEAN_NAME;
                }
            };
        }
    }
}
