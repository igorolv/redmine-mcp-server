package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.model.IssueHistoryView;
import ru.it_spectrum.ai.redmine.mcp.model.IssueTreeView;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;

import java.util.Map;

@Service
public class IssueTools {

    private final IssueService issueService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public IssueTools(IssueService issueService, JsonResponses json, ToolErrors errors) {
        this.issueService = issueService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "List issues in Redmine with flexible filtering by project, status, tracker, " +
            "assignee, priority, version, or saved query. Use statusId='*' to include closed issues. " +
            "Use queryId to apply a saved Redmine query (custom filter) — get available IDs via listQueries. " +
            "Use customFieldFilters to pass native Redmine filters like 'cf_10=rtk&cf_3=502167'. " +
            "Supports sorting and pagination.")
    public String listIssues(
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
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;
        Map<String, String> parsedCustomFieldFilters;
        try {
            parsedCustomFieldFilters = issueService.parseCustomFieldFilters(customFieldFilters);
        } catch (IllegalArgumentException e) {
            return errors.argument(e.getMessage());
        }

        var page = issueService.list(projectId, statusId, trackerId, assignedToId,
                priorityId, versionId, queryId, parsedCustomFieldFilters, sort, actualOffset, actualLimit);
        return json.write(page);
    }

    public String listIssues(String projectId, String statusId, Integer trackerId,
                             Integer assignedToId, Integer priorityId, Integer versionId,
                             Integer queryId, String sort, Integer limit, Integer offset) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                queryId, null, sort, limit, offset);
    }

    @McpTool(description = "Search across all Redmine content — issues, wiki pages, news, documents, etc. " +
            "Returns results of all types with title, type, URL, and description excerpt. " +
            "Use searchIssues if you only need issues with full details.")
    public String searchAll(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = issueService.searchAll(query, actualOffset, actualLimit);
        return json.write(result);
    }

    @McpTool(description = "Search for issues in Redmine using full-text search. " +
            "Returns a list of matching issues with their details (subject, status, assignee, etc). " +
            "Supports pagination via offset/limit parameters.")
    public String searchIssues(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = issueService.searchIssues(query, projectId, actualOffset, actualLimit);
        return json.write(result);
    }

    @McpTool(description = "List issues assigned to the currently authenticated user. " +
            "Convenient shortcut — no need to call getCurrentUser first. " +
            "Supports filtering by project, status, and sorting. Uses statusId='open' by default.")
    public String getMyIssues(
            @McpToolParam(description = "Project identifier to filter by (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open (default), closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var maybeResult = issueService.getMyIssues(projectId, statusId, sort, actualOffset, actualLimit);
        if (maybeResult.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(maybeResult.get());
    }

    @McpTool(description = "Build a full issue dependency tree: parent chain up to root, " +
            "subtasks down to specified depth, and direct relations. " +
            "Shows hierarchy with status and assignee for each node. " +
            "Useful for understanding task breakdown and dependencies at a glance.")
    public String getIssueTree(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How deep to traverse children, default 2, max 5", required = false) Integer depth
    ) {
        IssueTreeView view;
        try {
            view = issueService.getTree(issueId, depth);
        } catch (IssueNotFoundException e) {
            return errors.notFound("issue", "#" + e.issueId());
        }
        return json.write(view);
    }

    @McpTool(description = "Get the full change history of a Redmine issue. " +
            "Parses journal entries to show a timeline of status transitions, assignment changes, " +
            "priority changes, and other field modifications with human-readable names. " +
            "Also computes time spent in each status.")
    public String getIssueHistory(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        IssueHistoryView view;
        try {
            view = issueService.getHistory(issueId);
        } catch (IssueNotFoundException e) {
            return errors.notFound("issue", "#" + e.issueId());
        }
        return json.write(view);
    }

    @McpTool(description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, " +
            "subtasks (children), relations, notes (journals), and attachments list.")
    public String getIssue(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var maybeIssue = issueService.find(issueId);
        if (maybeIssue.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(maybeIssue.get());
    }

}
