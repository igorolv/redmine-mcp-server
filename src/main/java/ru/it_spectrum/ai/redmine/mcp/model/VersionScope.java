package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

public record VersionScope(
        int versionId,
        String name,
        RedmineVersion version,
        int totalIssues,
        int analyzedIssues,
        int closed,
        int open,
        int completionPercent,
        boolean truncated
) {
}
