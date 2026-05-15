package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

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
        List<RedmineIssue> topIssues
) {
}
