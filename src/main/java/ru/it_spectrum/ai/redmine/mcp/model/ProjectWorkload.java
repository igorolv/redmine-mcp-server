package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.Map;

public record ProjectWorkload(
        String project,
        int issueCount,
        double estimatedHours,
        Map<String, Integer> byPriority
) {
}
