package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.QueryPage;
import ru.it_spectrum.ai.redmine.mcp.api.RefList;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ReferenceDataService;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "reference-data", havingValue = "true", matchIfMissing = true)
public class ReferenceDataTools {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataTools.class);

    private final ReferenceDataService referenceDataService;
    private final RedmineMcpProperties properties;

    public ReferenceDataTools(ReferenceDataService referenceDataService, RedmineMcpProperties properties) {
        this.referenceDataService = referenceDataService;
        this.properties = properties;
    }

    @McpTool(
            description = "List issue statuses in Redmine; use their IDs as statusId in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RefList listStatuses() {
        log.info("Tool call: listStatuses");
        long start = System.nanoTime();
        var result = referenceDataService.listStatuses();
        ToolLogger.completed(log, "listStatuses", start);
        return RefList.of(result);
    }

    @McpTool(
            description = "List trackers (issue types) in Redmine; use their IDs as trackerId in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RefList listTrackers() {
        log.info("Tool call: listTrackers");
        long start = System.nanoTime();
        var result = referenceDataService.listTrackers();
        ToolLogger.completed(log, "listTrackers", start);
        return RefList.of(result);
    }

    @McpTool(
            description = "List issue priorities in Redmine; use their IDs as priorityId in listIssues.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RefList listPriorities() {
        log.info("Tool call: listPriorities");
        long start = System.nanoTime();
        var result = referenceDataService.listPriorities();
        ToolLogger.completed(log, "listPriorities", start);
        return RefList.of(result);
    }

    @McpTool(
            description = "List issue categories of a Redmine project.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RefList listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listIssueCategories (projectId={})", projectId);
        long start = System.nanoTime();
        var result = referenceDataService.listIssueCategories(projectId);
        ToolLogger.completed(log, "listIssueCategories", start);
        return RefList.of(result);
    }

    @McpTool(
            description = "List time entry activity types in Redmine, for interpreting time entries.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RefList listTimeEntryActivities() {
        log.info("Tool call: listTimeEntryActivities");
        long start = System.nanoTime();
        var result = referenceDataService.listTimeEntryActivities();
        ToolLogger.completed(log, "listTimeEntryActivities", start);
        return RefList.of(result);
    }

    @McpTool(
            description = "List saved queries (stored filters) in Redmine; apply one via listIssues(queryId), " +
            "especially for custom-field filters.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public QueryPage listQueries(
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
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
