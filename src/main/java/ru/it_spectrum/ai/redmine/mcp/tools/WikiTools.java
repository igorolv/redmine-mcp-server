package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.SearchResult;
import ru.it_spectrum.ai.redmine.mcp.api.WikiPage;
import ru.it_spectrum.ai.redmine.mcp.api.WikiPageList;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.WikiService;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "wiki", havingValue = "true", matchIfMissing = true)
public class WikiTools {

    private static final Logger log = LoggerFactory.getLogger(WikiTools.class);

    private final WikiService wikiService;
    private final RedmineMcpProperties properties;

    public WikiTools(WikiService wikiService, RedmineMcpProperties properties) {
        this.wikiService = wikiService;
        this.properties = properties;
    }

    @McpTool(
            description = "Get a wiki page from a Redmine project; content is in Textile/Markdown markup.",
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
            description = "List wiki pages in a Redmine project. Use getWikiPage to read a page's content.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public WikiPageList listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listWikiPages (projectId={})", projectId);
        long start = System.nanoTime();
        var result = wikiService.listPages(projectId);
        ToolLogger.completed(log, "listWikiPages", start);
        return WikiPageList.of(result);
    }

    @McpTool(
            description = "Search Redmine wiki pages using full-text search. " +
            "Use getWikiPage to read a page's full content.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public SearchResult searchWikiPages(
            @McpToolParam(description = "") String searchQuery,
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
    ) {
        log.info("Tool call: searchWikiPages (searchQuery={}, projectId={}, limit={}, offset={})",
                searchQuery, projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = wikiService.searchPages(searchQuery, projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "searchWikiPages", start);
        return result;
    }
}
