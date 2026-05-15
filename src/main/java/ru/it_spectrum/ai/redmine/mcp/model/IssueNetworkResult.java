package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;
import java.util.Map;

public record IssueNetworkResult(
        RedmineIssue root,
        int depth,
        boolean limitReached,
        Map<Integer, RedmineIssue> nodes,
        List<NetworkEdge> edges,
        Map<String, List<NetworkEdge>> edgesByType
) {
}
