package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

import java.util.List;
import java.util.Set;

public record ReleaseRisksResult(
        String projectId,
        int versionId,
        RedmineVersion version,
        int totalOpenIssues,
        int analyzedIssues,
        boolean truncated,
        Set<String> highPriorityNames,
        List<RiskCategory> categories,
        RiskScore score
) {
}
