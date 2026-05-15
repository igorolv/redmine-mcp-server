package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.List;

public record StaleIssuesResult(
        String projectId,
        int daysSinceUpdate,
        int limit,
        List<StaleIssue> issues,
        long oldestDaysSinceUpdated
) {
}
