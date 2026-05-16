package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;

public record MyIssuesResult(RedmineUser user, RedmineIssueSummary.Page page) {
}
