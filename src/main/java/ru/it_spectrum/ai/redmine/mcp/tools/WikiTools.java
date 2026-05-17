package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.SearchResult;
import ru.it_spectrum.ai.redmine.mcp.api.WikiPage;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.WikiService;

import java.util.List;

@Service
public class WikiTools {

    private static final Logger log = LoggerFactory.getLogger(WikiTools.class);

    private final WikiService wikiService;
    private final RedmineMcpProperties properties;

    public WikiTools(WikiService wikiService, RedmineMcpProperties properties) {
        this.wikiService = wikiService;
        this.properties = properties;
    }

    @McpTool(
            description = "Get a wiki page from a Redmine project. " +
            "Returns the page title, content (in Textile/Markdown markup), author, and attachments.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public WikiPage getWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Wiki page title (use 'Wiki' for the start page)") String pageTitle
    ) {
        log.info("Tool call: getWikiPage (projectId={}, pageTitle={})", projectId, pageTitle);
        long start = System.nanoTime();
        try {
            var result = wikiService.getPageOrThrow(projectId, pageTitle);
            ToolLogger.completed(log, "getWikiPage", start);
            return result;
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getWikiPage", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "List all wiki pages in a Redmine project. " +
            "Returns page titles and dates. Use getWikiPage to read a specific page's content.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public List<WikiPage> listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listWikiPages (projectId={})", projectId);
        long start = System.nanoTime();
        var result = wikiService.listPages(projectId);
        ToolLogger.completed(log, "listWikiPages", start);
        return result;
    }

    @McpTool(
            description = "Search Redmine wiki pages using full-text search. " +
            "Returns wiki search hits with title, URL, description excerpt, and datetime. " +
            "Use getWikiPage to read a specific page's full content.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public SearchResult searchWikiPages(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: searchWikiPages (query={}, projectId={}, limit={}, offset={})",
                query, projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = wikiService.searchPages(query, projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "searchWikiPages", start);
        return result;
    }
}
