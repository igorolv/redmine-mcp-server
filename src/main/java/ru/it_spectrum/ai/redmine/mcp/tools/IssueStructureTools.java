package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueHistory;
import ru.it_spectrum.ai.redmine.mcp.api.IssueTree;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;

/**
 * Structural / historical views of a single issue: the dependency tree and the interpreted
 * change-history timeline. These are heavier, lower-frequency reads than the core issue lookups,
 * so they live behind their own {@code redmine-mcp.tools.issue-structure} flag.
 */
@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "issue-structure", havingValue = "true", matchIfMissing = true)
public class IssueStructureTools {

    private static final Logger log = LoggerFactory.getLogger(IssueStructureTools.class);

    private final IssueService issueService;

    public IssueStructureTools(IssueService issueService) {
        this.issueService = issueService;
    }

    @McpTool(
            description = "Build a full issue dependency tree: parent chain up to root, " +
            "subtasks down to the given depth, and direct relations.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssueTree getIssueTree(
            @McpToolParam(description = "") int issueId,
            @McpToolParam(description = "How deep to traverse children", required = false) Integer depth
    ) {
        log.info("Tool call: getIssueTree (issueId={}, depth={})", issueId, depth);
        long start = System.nanoTime();
        IssueTree view;
        try {
            view = issueService.getTree(issueId, depth);
            ToolLogger.completed(log, "getIssueTree", start);
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getIssueTree", start, e.getMessage());
            throw e;
        }
        return view;
    }

    @McpTool(
            description = "Get an interpreted change-history timeline for an issue: human-readable field changes, " +
            "events with notes, and time aggregated per status. Use when you want just the change log, " +
            "not the full issue context.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssueHistory getIssueHistory(
            @McpToolParam(description = "") int issueId
    ) {
        log.info("Tool call: getIssueHistory (issueId={})", issueId);
        long start = System.nanoTime();
        var maybeHistory = issueService.getHistory(issueId);
        if (maybeHistory.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssueHistory", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getIssueHistory", start);
        return maybeHistory.get();
    }
}
