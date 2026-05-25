package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.IssuePage;
import ru.it_spectrum.ai.redmine.mcp.api.IssueTree;
import ru.it_spectrum.ai.redmine.mcp.api.MyIssues;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionOptions;
import ru.it_spectrum.ai.redmine.mcp.service.compression.IssueCompression;
import ru.it_spectrum.ai.redmine.mcp.service.compression.IssueFullContextCompression;

import java.util.Map;

@Service
public class IssueTools {

    private static final Logger log = LoggerFactory.getLogger(IssueTools.class);

    private final IssueService issueService;
    private final ContextService contextService;
    private final RedmineMcpProperties properties;
    private final IssueCompression issueCompression;
    private final IssueFullContextCompression contextCompression;

    public IssueTools(IssueService issueService, ContextService contextService, RedmineMcpProperties properties,
                      IssueCompression issueCompression, IssueFullContextCompression contextCompression) {
        this.issueService = issueService;
        this.contextService = contextService;
        this.properties = properties;
        this.issueCompression = issueCompression;
        this.contextCompression = contextCompression;
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
    public IssuePage listIssues(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open, closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Tracker ID to filter by (optional)", required = false) Integer trackerId,
            @McpToolParam(description = "Assigned user ID to filter by (optional)", required = false) Integer assignedToId,
            @McpToolParam(description = "Priority ID to filter by (optional)", required = false) Integer priorityId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId,
            @McpToolParam(description = "Saved query ID to apply (optional). Use listQueries to find available queries.", required = false) Integer queryId,
            @McpToolParam(description = "Custom field filters in query-string form, e.g. 'cf_10=rtk&cf_3=502167' (optional)", required = false) String customFieldFilters,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listIssues (projectId={}, statusId={}, trackerId={}, assignedToId={}, priorityId={}, versionId={}, queryId={}, customFieldFilters={}, sort={}, limit={}, offset={})",
                projectId, statusId, trackerId, assignedToId, priorityId, versionId, queryId, customFieldFilters, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();
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

    public IssuePage listIssues(String projectId, String statusId, Integer trackerId,
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
    public IssuePage searchIssues(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: searchIssues (query={}, projectId={}, limit={}, offset={})", query, projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

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
    public MyIssues getMyIssues(
            @McpToolParam(description = "Project identifier to filter by (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open (default), closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: getMyIssues (projectId={}, statusId={}, sort={}, limit={}, offset={})",
                projectId, statusId, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

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
    public IssueTree getIssueTree(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How deep to traverse children, uses configured default and max when omitted", required = false) Integer depth
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
            description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, " +
            "subtasks (children), relations, notes (journals), attachments list, " +
            "and associated repository changesets/revisions when visible to the Redmine user. " +
            "Use responseProfile='review' for implementation review: it keeps the issue text, " +
            "human journal notes, attachment metadata, and all changeset revisions while omitting verbose history.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public Issue getIssue(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Response shaping profile: default, review, or full. The review profile keeps review-relevant text and all changeset revisions while omitting verbose history.", required = false) String responseProfile
    ) {
        log.info("Tool call: getIssue (issueId={}, responseProfile={})", issueId, responseProfile);
        long start = System.nanoTime();
        var maybeIssue = issueService.find(issueId);
        if (maybeIssue.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssue", start, e.getMessage());
            throw e;
        }
        var compressed = issueCompression.compress(maybeIssue.get(), CompressionOptions.fromProfile(responseProfile));
        ToolLogger.completed(log, "getIssue", start);
        return compressed;
    }

    public Issue getIssue(int issueId) {
        return getIssue(issueId, null);
    }

    @McpTool(
            description = "Get full context needed to understand and implement a Redmine issue. " +
            "Returns: the issue with description, " +
            "interpreted history timeline with status durations, " +
            "nearby context issues with explicit roles (parent, sibling, child, related), " +
            "issue and parent attachments materialized like getAttachment, with text constrained by inline budgets " +
            "and image attachments included as localPath/fileUri links, " +
            "recent discussion notes, and truncation flags. Ideal first call when investigating a task. " +
            "Use responseProfile='review' when reviewing an implementation and you primarily need issue text, notes, attachments, and revisions.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssueFullContext getIssueFullContext(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Response shaping profile: default, review, or full. The review profile keeps review-relevant text and all changeset revisions while omitting verbose history.", required = false) String responseProfile
    ) {
        log.info("Tool call: getIssueFullContext (issueId={}, responseProfile={})", issueId, responseProfile);
        long start = System.nanoTime();
        var result = contextService.getIssueFullContext(issueId);
        if (result.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssueFullContext", start, e.getMessage());
            throw e;
        }
        var compressed = contextCompression.compress(result.get(), CompressionOptions.fromProfile(responseProfile));
        ToolLogger.completed(log, "getIssueFullContext", start);
        return compressed;
    }

    public IssueFullContext getIssueFullContext(int issueId) {
        return getIssueFullContext(issueId, null);
    }

}
