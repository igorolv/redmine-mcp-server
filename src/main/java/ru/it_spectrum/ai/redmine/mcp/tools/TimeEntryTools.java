package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class TimeEntryTools {
    private final RedmineClient client;

    public TimeEntryTools(RedmineClient client) {
        this.client = client;
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

        var page = client.getTimeEntries(projectId, issueId, userId, from, to, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Time entries: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.timeEntries().size()));

        for (var entry : page.timeEntries()) {
            appendTimeEntry(sb, entry);
        }

        return sb.toString();
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
        var user = client.getCurrentUser();
        if (user == null) {
            return "Could not retrieve current user";
        }

        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getTimeEntries(projectId, issueId, user.id(), from, to, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("My time entries (%s, %d total, showing %d-%d):\n\n".formatted(
                user.firstname() + " " + user.lastname(),
                page.totalCount(), page.offset() + 1,
                page.offset() + page.timeEntries().size()));

        for (var entry : page.timeEntries()) {
            appendTimeEntry(sb, entry);
        }

        return sb.toString();
    }

    private void appendTimeEntry(StringBuilder sb, ru.it_spectrum.ai.redmine.mcp.model.RedmineTimeEntry entry) {
        sb.append("- %s | %.2f h | %s | %s".formatted(
                entry.spentOn(), entry.hours(),
                entry.user() != null ? entry.user().name() : "—",
                entry.activity() != null ? entry.activity().name() : "—"));
        if (entry.issue() != null) {
            sb.append(" | Issue #%d".formatted(entry.issue().id()));
        }
        if (entry.comments() != null && !entry.comments().isBlank()) {
            sb.append("\n  %s".formatted(entry.comments()));
        }
        sb.append("\n");
    }
}
