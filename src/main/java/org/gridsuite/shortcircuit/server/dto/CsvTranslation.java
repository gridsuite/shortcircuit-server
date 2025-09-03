package org.gridsuite.shortcircuit.server.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record CsvTranslation(
    List<String> headersCsv,
    Map<String, String> enumValueTranslations,
    String language
) { }
