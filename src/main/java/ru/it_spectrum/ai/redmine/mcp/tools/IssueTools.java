package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.model.IssueFullContextResult;
import ru.it_spectrum.ai.redmine.mcp.model.IssueTreeView;
import ru.it_spectrum.ai.redmine.mcp.model.MyIssuesResult;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;

import java.util.Map;

@Service
public class IssueTools {

    private static final Logger log = LoggerFactory.getLogger(IssueTools.class);

    private final IssueService issueService;
    private final ContextService contextService;

    public IssueTools(IssueService issueService, ContextService contextService) {
        this.issueService = issueService;
        this.contextService = contextService;
    }

    @McpTool(
            description = "List issues in Redmine with flexible filtering by project, status, tracker, " +
            "assignee, priority, version, or saved query. Use statusId='*' to include closed issues. " +
            "Use queryId to apply a saved Redmine query (custom filter) — get available IDs via listQueries. " +
            "Use customFieldFilters to pass native Redmine filters like 'cf_10=rtk&cf_3=502167'. " +
            "Supports sorting and pagination.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RedmineIssueSummary.Page listIssues(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open, closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Tracker ID to filter by (optional)", required = false) Integer trackerId,
            @McpToolParam(description = "Assigned user ID to filter by (optional)", required = false) Integer assignedToId,
            @McpToolParam(description = "Priority ID to filter by (optional)", required = false) Integer priorityId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId,
            @McpToolParam(description = "Saved query ID to apply (optional). Use listQueries to find available queries.", required = false) Integer queryId,
            @McpToolParam(description = "Custom field filters in query-string form, e.g. 'cf_10=rtk&cf_3=502167' (optional)", required = false) String customFieldFilters,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listIssues (projectId={}, statusId={}, trackerId={}, assignedToId={}, priorityId={}, versionId={}, queryId={}, customFieldFilters={}, sort={}, limit={}, offset={})",
                projectId, statusId, trackerId, assignedToId, priorityId, versionId, queryId, customFieldFilters, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;
        Map<String, String> parsedCustomFieldFilters;
        try {
            parsedCustomFieldFilters = issueService.parseCustomFieldFilters(customFieldFilters);
        } catch (IllegalArgumentException e) {
            ToolLogger.failed(log, "listIssues", start, e.getMessage());
            throw e;
        }

        var page = issueService.list(projectId, statusId, trackerId, assignedToId,
                priorityId, versionId, queryId, parsedCustomFieldFilters, sort, actualOffset, actualLimit);
        ToolLogger.completed(log, "listIssues", start);
        return page;
    }

    public RedmineIssueSummary.Page listIssues(String projectId, String statusId, Integer trackerId,
                                               Integer assignedToId, Integer priorityId, Integer versionId,
                                               Integer queryId, String sort, Integer limit, Integer offset) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                queryId, null, sort, limit, offset);
    }

    @McpTool(
            description = "Search for issues in Redmine using full-text search. " +
            "Returns a list of matching issues with their details (subject, status, assignee, etc). " +
            "Supports pagination via offset/limit parameters.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RedmineClient.SearchWithIssueSummaries searchIssues(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: searchIssues (query={}, projectId={}, limit={}, offset={})", query, projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = issueService.searchIssues(query, projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "searchIssues", start);
        return result;
    }

    @McpTool(
            description = "List issues assigned to the currently authenticated user. " +
            "Convenient shortcut — no need to call getCurrentUser first. " +
            "Supports filtering by project, status, and sorting. Uses statusId='open' by default.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MyIssuesResult getMyIssues(
            @McpToolParam(description = "Project identifier to filter by (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open (default), closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: getMyIssues (projectId={}, statusId={}, sort={}, limit={}, offset={})",
                projectId, statusId, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var maybeResult = issueService.getMyIssues(projectId, statusId, sort, actualOffset, actualLimit);
        if (maybeResult.isEmpty()) {
            var e = new ResourceUnavailableException("current user");
            ToolLogger.failed(log, "getMyIssues", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getMyIssues", start);
        return maybeResult.get();
    }

    @McpTool(
            description = "Build a full issue dependency tree: parent chain up to root, " +
            "subtasks down to specified depth, and direct relations. " +
            "Shows hierarchy with status and assignee for each node. " +
            "Useful for understanding task breakdown and dependencies at a glance.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssueTreeView getIssueTree(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How deep to traverse children, default 2, max 5", required = false) Integer depth
    ) {
        log.info("Tool call: getIssueTree (issueId={}, depth={})", issueId, depth);
        long start = System.nanoTime();
        IssueTreeView view;
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
            description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, " +
            "subtasks (children), relations, notes (journals), attachments list, " +
            "and associated repository changesets/revisions when visible to the Redmine user.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RedmineIssue getIssue(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        log.info("Tool call: getIssue (issueId={})", issueId);
        long start = System.nanoTime();
        var maybeIssue = issueService.find(issueId);
        if (maybeIssue.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssue", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getIssue", start);
        return maybeIssue.get();
    }

    @McpTool(
            description = "Get full context needed to understand and implement a Redmine issue. " +
            "Returns: the issue with description, " +
            "interpreted history timeline with status durations, " +
            "nearby context issues with explicit roles (parent, sibling, child, related), " +
            "supported text/document attachments extracted inline (text, PDF, DOCX, XLSX, PPTX, ZIP), " +
            "recent discussion notes, and truncation flags. Ideal first call when investigating a task.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssueFullContextResult getIssueFullContext(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        log.info("Tool call: getIssueFullContext (issueId={})", issueId);
        long start = System.nanoTime();
        var result = contextService.getIssueFullContext(issueId);
        if (result.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssueFullContext", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getIssueFullContext", start);
        return result.get();
    }

}
