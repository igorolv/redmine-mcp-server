package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.MyTimeEntries;
import ru.it_spectrum.ai.redmine.mcp.api.TimeEntryPage;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryService;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "time-entry", havingValue = "true", matchIfMissing = true)
public class TimeEntryTools {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryTools.class);

    private final TimeEntryService timeEntryService;
    private final RedmineMcpProperties properties;

    public TimeEntryTools(TimeEntryService timeEntryService, RedmineMcpProperties properties) {
        this.timeEntryService = timeEntryService;
        this.properties = properties;
    }

    @McpTool(
            description = "List time entries (logged hours) in Redmine. " +
            "Filter by project, issue, user, or date range.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public TimeEntryPage listTimeEntries(
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "", required = false) Integer issueId,
            @McpToolParam(description = "", required = false) Integer userId,
            @McpToolParam(description = "From date, YYYY-MM-DD", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD", required = false) String to,
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
    ) {
        log.info("Tool call: listTimeEntries (projectId={}, issueId={}, userId={}, from={}, to={}, limit={}, offset={})",
                projectId, issueId, userId, from, to, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = timeEntryService.list(projectId, issueId, userId, from, to, actualOffset, actualLimit);
        ToolLogger.completed(log, "listTimeEntries", start);
        return result;
    }

    @McpTool(
            description = "List time entries (logged hours) for the currently authenticated user — " +
            "no need to call getCurrentUser first.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MyTimeEntries getMyTimeEntries(
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "", required = false) Integer issueId,
            @McpToolParam(description = "From date, YYYY-MM-DD", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD", required = false) String to,
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
    ) {
        log.info("Tool call: getMyTimeEntries (projectId={}, issueId={}, from={}, to={}, limit={}, offset={})",
                projectId, issueId, from, to, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

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
