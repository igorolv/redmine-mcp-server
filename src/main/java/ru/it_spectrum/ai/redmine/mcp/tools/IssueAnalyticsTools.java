package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.BlockerChain;
import ru.it_spectrum.ai.redmine.mcp.api.StaleIssues;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

/**
 * Issue-centric analytics: tracing blocking dependency chains and surfacing stale issues. These
 * depend only on issue data (no project/version scoping), so they sit in their own
 * {@code redmine-mcp.tools.issue-analytics} group, separate from release/PM reporting.
 */
@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "issue-analytics", havingValue = "true", matchIfMissing = true)
public class IssueAnalyticsTools {

    private static final Logger log = LoggerFactory.getLogger(IssueAnalyticsTools.class);

    private final AnalysisService analysisService;

    public IssueAnalyticsTools(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @McpTool(
            description = "Trace the blocking dependency chain for an issue. " +
            "Shows what blocks this issue (must be resolved first) and what this issue blocks. " +
            "Follows blocks/blocked_by relations recursively to reveal the critical path, " +
            "bounded by depth 10 and 30 fetched issues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public BlockerChain getBlockerChain(
            @McpToolParam(description = "Issue ID") int issueId
    ) {
        log.info("Tool call: getBlockerChain (issueId={})", issueId);
        long start = System.nanoTime();
        try {
            var result = analysisService.getBlockerChain(issueId);
            ToolLogger.completed(log, "getBlockerChain", start);
            return result;
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getBlockerChain", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Find open issues that haven't been updated for a specified number of days. " +
            "Sorted by staleness (oldest first). Useful for identifying neglected or forgotten tasks.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public StaleIssues getStaleIssues(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Minimum days since last update", required = false) Integer daysSinceUpdate,
            @McpToolParam(description = "Maximum number of results", required = false) Integer limit
    ) {
        log.info("Tool call: getStaleIssues (projectId={}, daysSinceUpdate={}, limit={})",
                projectId, daysSinceUpdate, limit);
        long start = System.nanoTime();
        var result = analysisService.getStaleIssues(projectId, daysSinceUpdate, limit);
        ToolLogger.completed(log, "getStaleIssues", start);
        return result;
    }
}
