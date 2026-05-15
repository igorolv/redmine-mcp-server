package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record VersionComparisonResult(
        String projectId,
        VersionScope first,
        VersionScope second,
        List<RedmineIssue> onlyInFirst,
        List<RedmineIssue> onlyInSecond,
        List<RedmineIssue> inBoth
) {
}
