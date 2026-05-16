package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.model.MyTimeEntriesResult;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryService;

@Service
public class TimeEntryTools {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryTools.class);

    private final TimeEntryService timeEntryService;

    public TimeEntryTools(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @McpTool(
            description = "List time entries (time tracking / logged hours) in Redmine. " +
            "Filter by project, issue, user, or date range. " +
            "Returns hours, activity type, user, date, and comments.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RedmineTimeEntry.Page listTimeEntries(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Issue ID to filter by (optional)", required = false) Integer issueId,
            @McpToolParam(description = "User ID to filter by (optional)", required = false) Integer userId,
            @McpToolParam(description = "From date, YYYY-MM-DD (optional)", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD (optional)", required = false) String to,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listTimeEntries (projectId={}, issueId={}, userId={}, from={}, to={}, limit={}, offset={})",
                projectId, issueId, userId, from, to, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = timeEntryService.list(projectId, issueId, userId, from, to, actualOffset, actualLimit);
        ToolLogger.completed(log, "listTimeEntries", start);
        return result;
    }

    @McpTool(
            description = "List time entries for the currently authenticated user. " +
            "Convenient shortcut — no need to call getCurrentUser first. " +
            "Filter by project, issue, or date range. " +
            "Returns hours, activity type, date, and comments.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MyTimeEntriesResult getMyTimeEntries(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Issue ID to filter by (optional)", required = false) Integer issueId,
            @McpToolParam(description = "From date, YYYY-MM-DD (optional)", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD (optional)", required = false) String to,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: getMyTimeEntries (projectId={}, issueId={}, from={}, to={}, limit={}, offset={})",
                projectId, issueId, from, to, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = timeEntryService.getMyTimeEntries(projectId, issueId, from, to, actualOffset, actualLimit);
        if (result.isEmpty()) {
            var e = new ResourceUnavailableException("current user");
            ToolLogger.failed(log, "getMyTimeEntries", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getMyTimeEntries", start);
        return result.get();
    }

}
