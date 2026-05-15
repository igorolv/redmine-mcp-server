package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

public record BlockerNode(RedmineIssue issue, int depth) {
}
