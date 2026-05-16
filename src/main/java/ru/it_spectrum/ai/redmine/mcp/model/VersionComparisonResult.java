package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

import java.util.List;

public record VersionComparisonResult(
        String projectId,
        VersionScope first,
        VersionScope second,
        List<RedmineIssueSummary> onlyInFirst,
        List<RedmineIssueSummary> onlyInSecond,
        List<RedmineIssueSummary> inBoth
) {
}
