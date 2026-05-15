package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ReferenceDataService;

@Service
public class ReferenceDataTools {
    private final ReferenceDataService referenceDataService;
    private final JsonResponses json;

    public ReferenceDataTools(ReferenceDataService referenceDataService, JsonResponses json) {
        this.referenceDataService = referenceDataService;
        this.json = json;
    }

    @McpTool(description = "List all available issue statuses in Redmine. " +
            "Returns status IDs and names. Use these IDs for filtering in listIssues.")
    public String listStatuses() {
        return json.write(referenceDataService.listStatuses());
    }

    @McpTool(description = "List all available trackers in Redmine. " +
            "Returns tracker IDs and names. Use these IDs for filtering in listIssues.")
    public String listTrackers() {
        return json.write(referenceDataService.listTrackers());
    }

    @McpTool(description = "List all available issue priorities in Redmine. " +
            "Returns priority IDs and names. Use these IDs for filtering in listIssues.")
    public String listPriorities() {
        return json.write(referenceDataService.listPriorities());
    }

    @McpTool(description = "List issue categories for a specific Redmine project. " +
            "Returns category IDs and names. Categories are project-specific.")
    public String listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        return json.write(referenceDataService.listIssueCategories(projectId));
    }

    @McpTool(description = "List all available time entry activity types in Redmine. " +
            "Returns activity IDs and names. Use these IDs when logging time entries.")
    public String listTimeEntryActivities() {
        return json.write(referenceDataService.listTimeEntryActivities());
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

        return json.write(referenceDataService.listQueries(actualOffset, actualLimit));
    }
}
