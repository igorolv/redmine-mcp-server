package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record BlockerChainResult(
        RedmineIssue root,
        List<BlockerNode> blockedBy,
        List<BlockerNode> blocks,
        int chainDepth,
        int totalIssues
) {
}
