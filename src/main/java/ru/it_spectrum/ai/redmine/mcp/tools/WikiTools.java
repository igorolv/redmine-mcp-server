package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.WikiService;

@Service
public class WikiTools {

    private static final Logger log = LoggerFactory.getLogger(WikiTools.class);

    private final WikiService wikiService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public WikiTools(WikiService wikiService, JsonResponses json, ToolErrors errors) {
        this.wikiService = wikiService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get a wiki page from a Redmine project. " +
            "Returns the page title, content (in Textile/Markdown markup), author, and attachments.")
    public String getWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Wiki page title (use 'Wiki' for the start page)") String pageTitle
    ) {
        log.info("Tool call: getWikiPage (projectId={}, pageTitle={})", projectId, pageTitle);
        long start = System.nanoTime();
        try {
            var result = wikiService.getPageOrThrow(projectId, pageTitle);
            ToolLogger.completed(log, "getWikiPage", start);
            return json.write(result);
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getWikiPage", start, e.getMessage());
            return errors.notFound(e.resource(), e.id());
        }
    }

    @McpTool(description = "List all wiki pages in a Redmine project. " +
            "Returns page titles and dates. Use getWikiPage to read a specific page's content.")
    public String listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listWikiPages (projectId={})", projectId);
        long start = System.nanoTime();
        var result = wikiService.listPages(projectId);
        ToolLogger.completed(log, "listWikiPages", start);
        return json.write(result);
    }
}
