package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record ContextIssue(
        RedmineIssue issue,
        List<IssueContextRole> roles
) {
}
