package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.TimeEntryMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryMutationService;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class TimeEntryWriteTools {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryWriteTools.class);

    private final TimeEntryMutationService mutationService;

    public TimeEntryWriteTools(TimeEntryMutationService mutationService) {
        this.mutationService = mutationService;
    }

    @McpTool(
            description = "Record logged hours for the API-key user against exactly one issue or project. This is " +
            "non-idempotent and retries can duplicate time; use listTimeEntryActivities when an activity ID is needed.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    public TimeEntryMutationResult createTimeEntry(
            @McpToolParam(description = "", required = false) Integer issueId,
            @McpToolParam(description = "", required = false) Integer projectId,
            @McpToolParam(description = "") double hours,
            @McpToolParam(description = "From listTimeEntryActivities", required = false) Integer activityId,
            @McpToolParam(description = "YYYY-MM-DD", required = false) String spentOn,
            @McpToolParam(description = "", required = false) String comments,
            @McpToolParam(description = "JSON object: field ID to scalar or scalar array", required = false) String customFieldsJson
    ) {
        log.info("Tool call: createTimeEntry (issueId={}, projectId={}, hours={}, activityId={}, spentOn={})",
                issueId, projectId, hours, activityId, spentOn);
        long start = System.nanoTime();
        try {
            var result = mutationService.createTimeEntry(
                    issueId, projectId, hours, activityId, spentOn, comments, customFieldsJson);
            ToolLogger.completed(log, "createTimeEntry", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "createTimeEntry", start, e.getMessage());
            throw e;
        }
    }
}
