package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.SearchResult;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.SearchService;

@Service
public class SearchTools {

    private static final Logger log = LoggerFactory.getLogger(SearchTools.class);

    private final SearchService searchService;
    private final RedmineMcpProperties properties;

    public SearchTools(SearchService searchService, RedmineMcpProperties properties) {
        this.searchService = searchService;
        this.properties = properties;
    }

    @McpTool(
            description = "Search across Redmine content. Returns generic search hits with title, type, URL, " +
            "description excerpt, and datetime. Use searchIssues for issue summaries and searchWikiPages for wiki-only results.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public SearchResult searchAll(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Comma-separated content types to include: issues, wiki_pages, news, documents, changesets, messages, projects (optional)", required = false) String types,
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: searchAll (query={}, projectId={}, types={}, limit={}, offset={})",
                query, projectId, types, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        try {
            var result = searchService.searchAll(query, projectId, types, actualOffset, actualLimit);
            ToolLogger.completed(log, "searchAll", start);
            return result;
        } catch (IllegalArgumentException e) {
            ToolLogger.failed(log, "searchAll", start, e.getMessage());
            throw e;
        }
    }
}
