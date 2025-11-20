package org.gridsuite.shortcircuit.server.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record CsvExportParams(
    List<String> csvHeader,
    Map<String, String> enumValueTranslations,
    String language,
    boolean oneBusCase
) { }
