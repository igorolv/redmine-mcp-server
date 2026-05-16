package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

import java.util.List;

public record UserWorkloadResult(
        int userId,
        String userName,
        String projectId,
        int totalOpenIssues,
        int analyzedIssues,
        boolean truncated,
        HoursSummary hours,
        int overdue,
        List<ProjectWorkload> byProject,
        List<RedmineIssueSummary> topIssues
) {
}
