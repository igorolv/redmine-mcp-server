package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.WikiMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.WikiMutationService;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class WikiWriteTools {

    private static final Logger log = LoggerFactory.getLogger(WikiWriteTools.class);

    private final WikiMutationService mutationService;

    public WikiWriteTools(WikiMutationService mutationService) {
        this.mutationService = mutationService;
    }

    @McpTool(
            description = "Create a new Redmine wiki page with its complete initial text and optional parent. This " +
            "writes as the API-key user and fails if the title already exists; use updateWikiPage for an existing page.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    public WikiMutationResult createWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "") String pageTitle,
            @McpToolParam(description = "") String text,
            @McpToolParam(description = "Must name an existing page", required = false) String parentTitle,
            @McpToolParam(description = "", required = false) String comments
    ) {
        log.info("Tool call: createWikiPage (projectId={}, pageTitle={}, parentTitle={}, textLength={})",
                projectId, pageTitle, parentTitle, text != null ? text.length() : null);
        long start = System.nanoTime();
        try {
            var result = mutationService.createPage(projectId, pageTitle, text, parentTitle, comments);
            ToolLogger.completed(log, "createWikiPage", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "createWikiPage", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Replace the complete text of one existing Redmine wiki page; this is not a patch or " +
            "append operation. Pass the current version from getWikiPage for optimistic locking, and use createWikiPage if absent.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    public WikiMutationResult updateWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "") String pageTitle,
            @McpToolParam(description = "") String text,
            @McpToolParam(description = "From getWikiPage") int version,
            @McpToolParam(description = "", required = false) String comments
    ) {
        log.info("Tool call: updateWikiPage (projectId={}, pageTitle={}, version={}, textLength={})",
                projectId, pageTitle, version, text != null ? text.length() : null);
        long start = System.nanoTime();
        try {
            var result = mutationService.updatePage(projectId, pageTitle, text, version, comments);
            ToolLogger.completed(log, "updateWikiPage", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "updateWikiPage", start, e.getMessage());
            throw e;
        }
    }
}
