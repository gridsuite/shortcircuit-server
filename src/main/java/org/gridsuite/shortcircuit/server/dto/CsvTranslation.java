package org.gridsuite.shortcircuit.server.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Builder
public record CsvTranslation(
        List<String> headersCsv,
        Map<String, String> enumValueTranslations
) { }
