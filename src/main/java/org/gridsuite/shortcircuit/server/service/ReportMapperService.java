/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.shortcircuit.server.service;

import com.powsybl.commons.reporter.Reporter;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.shortcircuit.server.service.reports.ReportMapper;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class manage how to modify logs of a computation results with implementations found.
 *
 * @see ReportMapper
 */
@Slf4j
@Component
public class ReportMapperService {
    @Getter private final Set<ReportMapper> reportMappers = new HashSet<>();

    ReportMapperService(@NonNull final Set<ReportMapper> reportMappers) {
        this.reportMappers.addAll(reportMappers);
    }

    /**
     * Get implementations of {@link ReportMapper}.
     * @apiNote use Spring {@link SpringFactoriesLoader} and Java {@link ServiceLoader} mechanisms to let users choice
     */
    @Autowired
    public ReportMapperService(@Nullable final Collection<ReportMapper> reportMapperBeans) {
        final BinaryOperator<ReportMapper> mostRecent = (rm1, rm2) -> rm1.getVersion() > rm2.getVersion() ? rm1 : rm2;
        /* load instances from ServiceLoader (META-INF/services/*) system and SpringFactoriesLoader (META-INF/spring.factories) system */
        final HashMap<String, ReportMapper> dedup = Stream.concat(
            ServiceLoader.load(ReportMapper.class, this.getClass().getClassLoader()).stream().map(ServiceLoader.Provider::get),
            SpringFactoriesLoader.forDefaultResourceLocation(this.getClass().getClassLoader()).load(ReportMapper.class).stream()
        ).collect(Collectors.toMap(ReportMapper::getName, rm -> rm, mostRecent, HashMap::new));
        /* and add beans found by Spring scan */
        if (reportMapperBeans != null && !reportMapperBeans.isEmpty()) {
            reportMapperBeans.forEach(bean -> dedup.merge(bean.getName(), bean, mostRecent));
        }
        this.reportMappers.addAll(dedup.values());
    }

    /**
     * Analyze and modify reports using {@link ReportMapper mappers} registered
     * @param reporter the reporter to analyze
     * @return the result of treatment, or same instance if nothing done
     */
    public Reporter modifyReporter(@NonNull final Reporter reporter) {
        Reporter result = reporter;
        for (final ReportMapper mapper : this.getReportMappers()) {
            result = mapper.mapReporter(result);
        }
        return result;
    }
}
