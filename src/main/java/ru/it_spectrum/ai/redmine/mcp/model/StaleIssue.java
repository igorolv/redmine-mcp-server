package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

public record StaleIssue(RedmineIssueSummary issue, long daysSinceUpdated, boolean overdue) {
}
