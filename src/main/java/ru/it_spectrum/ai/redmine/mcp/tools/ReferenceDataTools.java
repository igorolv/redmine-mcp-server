package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class ReferenceDataTools {
    private final RedmineClient client;
    private final JsonResponses json;

    public ReferenceDataTools(RedmineClient client, JsonResponses json) {
        this.client = client;
        this.json = json;
    }

    @McpTool(description = "List all available issue statuses in Redmine. " +
            "Returns status IDs and names. Use these IDs for filtering in listIssues.")
    public String listStatuses() {
        var statuses = client.getIssueStatuses();
        return json.write(statuses);
    }

    @McpTool(description = "List all available trackers in Redmine. " +
            "Returns tracker IDs and names. Use these IDs for filtering in listIssues.")
    public String listTrackers() {
        var trackers = client.getTrackers();
        return json.write(trackers);
    }

    @McpTool(description = "List all available issue priorities in Redmine. " +
            "Returns priority IDs and names. Use these IDs for filtering in listIssues.")
    public String listPriorities() {
        var priorities = client.getIssuePriorities();
        return json.write(priorities);
    }

    @McpTool(description = "List issue categories for a specific Redmine project. " +
            "Returns category IDs and names. Categories are project-specific.")
    public String listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var categories = client.getIssueCategories(projectId);
        return json.write(categories);
    }

    @McpTool(description = "List all available time entry activity types in Redmine. " +
            "Returns activity IDs and names. Use these IDs when logging time entries.")
    public String listTimeEntryActivities() {
        var activities = client.getTimeEntryActivities();
        return json.write(activities);
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
        return json.write(page);
    }
}
