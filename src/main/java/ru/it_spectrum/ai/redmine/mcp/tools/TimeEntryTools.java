package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryService;

@Service
public class TimeEntryTools {
    private final TimeEntryService timeEntryService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public TimeEntryTools(TimeEntryService timeEntryService, JsonResponses json, ToolErrors errors) {
        this.timeEntryService = timeEntryService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "List time entries (time tracking / logged hours) in Redmine. " +
            "Filter by project, issue, user, or date range. " +
            "Returns hours, activity type, user, date, and comments.")
    public String listTimeEntries(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Issue ID to filter by (optional)", required = false) Integer issueId,
            @McpToolParam(description = "User ID to filter by (optional)", required = false) Integer userId,
            @McpToolParam(description = "From date, YYYY-MM-DD (optional)", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD (optional)", required = false) String to,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        return json.write(timeEntryService.list(projectId, issueId, userId, from, to, actualOffset, actualLimit));
    }

    @McpTool(description = "List time entries for the currently authenticated user. " +
            "Convenient shortcut — no need to call getCurrentUser first. " +
            "Filter by project, issue, or date range. " +
            "Returns hours, activity type, date, and comments.")
    public String getMyTimeEntries(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Issue ID to filter by (optional)", required = false) Integer issueId,
            @McpToolParam(description = "From date, YYYY-MM-DD (optional)", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD (optional)", required = false) String to,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = timeEntryService.getMyTimeEntries(projectId, issueId, from, to, actualOffset, actualLimit);
        if (result.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(result.get());
    }

}
