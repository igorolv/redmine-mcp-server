package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record SiblingsResult(
        RedmineIssue issue,
        RedmineIssue parent,
        List<RedmineIssue> siblings,
        int closed,
        int total,
        int progressPercent,
        String status
) {
}
