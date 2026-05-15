package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

public record StaleIssue(RedmineIssue issue, long daysSinceUpdated, boolean overdue) {
}
