package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

import java.util.List;
import java.util.Map;

public record VersionChangelogResult(
        String projectId,
        int versionId,
        RedmineVersion version,
        int totalIssues,
        int analyzedIssues,
        boolean truncated,
        Map<String, List<RedmineIssue>> byTracker,
        IssueCountSummary counts,
        HoursSummary hours
) {
}
