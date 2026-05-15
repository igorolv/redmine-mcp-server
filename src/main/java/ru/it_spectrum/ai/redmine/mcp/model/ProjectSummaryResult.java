package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.List;
import java.util.Map;

public record ProjectSummaryResult(
        String projectId,
        Integer versionId,
        IssueCountSummary counts,
        boolean truncated,
        int analyzedOpenIssues,
        Map<String, Integer> byStatus,
        Map<String, Integer> byTracker,
        Map<String, Integer> byPriority,
        List<AssigneeSummary> byAssignee,
        int overdue,
        HoursSummary hours
) {
}
