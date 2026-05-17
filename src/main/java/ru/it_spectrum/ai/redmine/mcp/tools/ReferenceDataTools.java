package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.QueryPage;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ReferenceDataService;

import java.util.List;

@Service
public class ReferenceDataTools {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataTools.class);

    private final ReferenceDataService referenceDataService;
    private final RedmineMcpProperties properties;

    public ReferenceDataTools(ReferenceDataService referenceDataService, RedmineMcpProperties properties) {
        this.referenceDataService = referenceDataService;
        this.properties = properties;
    }

    @McpTool(
            description = "List all available issue statuses in Redmine. " +
            "Returns status IDs and names. Use these IDs for filtering in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<Ref> listStatuses() {
        log.info("Tool call: listStatuses");
        long start = System.nanoTime();
        var result = referenceDataService.listStatuses();
        ToolLogger.completed(log, "listStatuses", start);
        return result;
    }

    @McpTool(
            description = "List all available trackers in Redmine. " +
            "Returns tracker IDs and names. Use these IDs for filtering in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<Ref> listTrackers() {
        log.info("Tool call: listTrackers");
        long start = System.nanoTime();
        var result = referenceDataService.listTrackers();
        ToolLogger.completed(log, "listTrackers", start);
        return result;
    }

    @McpTool(
            description = "List all available issue priorities in Redmine. " +
            "Returns priority IDs and names. Use these IDs for filtering in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<Ref> listPriorities() {
        log.info("Tool call: listPriorities");
        long start = System.nanoTime();
        var result = referenceDataService.listPriorities();
        ToolLogger.completed(log, "listPriorities", start);
        return result;
    }

    @McpTool(
            description = "List issue categories for a specific Redmine project. " +
            "Returns category IDs and names. Categories are project-specific.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<Ref> listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listIssueCategories (projectId={})", projectId);
        long start = System.nanoTime();
        var result = referenceDataService.listIssueCategories(projectId);
        ToolLogger.completed(log, "listIssueCategories", start);
        return result;
    }

    @McpTool(
            description = "List all available time entry activity types in Redmine. " +
            "Returns activity IDs and names for interpreting existing time entries.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<Ref> listTimeEntryActivities() {
        log.info("Tool call: listTimeEntryActivities");
        long start = System.nanoTime();
        var result = referenceDataService.listTimeEntryActivities();
        ToolLogger.completed(log, "listTimeEntryActivities", start);
        return result;
    }

    @McpTool(
            description = "List saved queries (custom filters) available in Redmine. " +
            "Returns query IDs and names. Use the query ID with listIssues(queryId) " +
            "to apply a saved filter — especially useful for queries that use custom fields.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public QueryPage listQueries(
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listQueries (limit={}, offset={})", limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = referenceDataService.listQueries(actualOffset, actualLimit);
        ToolLogger.completed(log, "listQueries", start);
        return result;
    }
}
