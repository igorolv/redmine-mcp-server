package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class ReferenceDataTools {
    private final RedmineClient client;

    public ReferenceDataTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "List all available issue statuses in Redmine. " +
            "Returns status IDs and names. Use these IDs for filtering in listIssues.")
    public String listStatuses() {
        var statuses = client.getIssueStatuses();
        if (statuses.isEmpty()) {
            return "No statuses found";
        }
        var sb = new StringBuilder("Issue statuses:\n\n");
        for (var s : statuses) {
            sb.append("- [%d] %s\n".formatted(s.id(), s.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available trackers in Redmine. " +
            "Returns tracker IDs and names. Use these IDs for filtering in listIssues.")
    public String listTrackers() {
        var trackers = client.getTrackers();
        if (trackers.isEmpty()) {
            return "No trackers found";
        }
        var sb = new StringBuilder("Trackers:\n\n");
        for (var t : trackers) {
            sb.append("- [%d] %s\n".formatted(t.id(), t.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available issue priorities in Redmine. " +
            "Returns priority IDs and names. Use these IDs for filtering in listIssues.")
    public String listPriorities() {
        var priorities = client.getIssuePriorities();
        if (priorities.isEmpty()) {
            return "No priorities found";
        }
        var sb = new StringBuilder("Issue priorities:\n\n");
        for (var p : priorities) {
            sb.append("- [%d] %s\n".formatted(p.id(), p.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List issue categories for a specific Redmine project. " +
            "Returns category IDs and names. Categories are project-specific.")
    public String listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var categories = client.getIssueCategories(projectId);
        if (categories.isEmpty()) {
            return "No issue categories found for project: " + projectId;
        }
        var sb = new StringBuilder("Issue categories for project %s:\n\n".formatted(projectId));
        for (var c : categories) {
            sb.append("- [%d] %s\n".formatted(c.id(), c.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available time entry activity types in Redmine. " +
            "Returns activity IDs and names. Use these IDs when logging time entries.")
    public String listTimeEntryActivities() {
        var activities = client.getTimeEntryActivities();
        if (activities.isEmpty()) {
            return "No time entry activities found";
        }
        var sb = new StringBuilder("Time entry activities:\n\n");
        for (var a : activities) {
            sb.append("- [%d] %s\n".formatted(a.id(), a.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List saved queries (custom filters) available in Redmine. " +
            "Returns query IDs and names. Use the query ID with listIssues(queryId) " +
            "to apply a saved filter — especially useful for queries that use custom fields.")
    public String listQueries(
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getQueries(actualOffset, actualLimit);
        if (page.queries().isEmpty()) {
            return "No saved queries found";
        }

        var sb = new StringBuilder();
        sb.append("Saved queries: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.queries().size()));

        for (var q : page.queries()) {
            sb.append("- [%d] %s".formatted(q.id(), q.name()));
            if (q.projectId() != null) {
                sb.append(" (project #%d)".formatted(q.projectId()));
            }
            if (q.isPublic()) {
                sb.append(" [public]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
