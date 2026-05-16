package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;

@Service
public class ContextTools {

    private static final Logger log = LoggerFactory.getLogger(ContextTools.class);

    private final ContextService contextService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public ContextTools(ContextService contextService, JsonResponses json, ToolErrors errors) {
        this.contextService = contextService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get full context needed to understand and implement a Redmine issue. " +
            "One call replaces 10+ separate tool calls. Returns: the issue with description, " +
            "interpreted history timeline with status durations, " +
            "nearby context issues with explicit roles (parent, sibling, child, related), " +
            "document attachments extracted inline (PDF/DOCX/XLSX), recent discussion notes, " +
            "and truncation flags. Ideal first call when investigating or implementing a task.")
    public String getIssueFullContext(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        log.info("Tool call: getIssueFullContext (issueId={})", issueId);
        long start = System.nanoTime();
        var result = contextService.getIssueFullContext(issueId);
        ToolLogger.completed(log, "getIssueFullContext", start);
        if (result.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(result.get());
    }
}
