package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record RelatedClosedIssuesResult(
        RedmineIssue issue,
        List<RedmineIssue> direct,
        List<RedmineIssue> siblings,
        List<RedmineIssue> similar,
        int total
) {
}
