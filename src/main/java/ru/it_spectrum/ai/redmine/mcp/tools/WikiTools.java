package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class WikiTools {
    private final RedmineClient client;
    private final JsonResponses json;
    private final ToolErrors errors;

    public WikiTools(RedmineClient client, JsonResponses json, ToolErrors errors) {
        this.client = client;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get a wiki page from a Redmine project. " +
            "Returns the page title, content (in Textile/Markdown markup), author, and attachments.")
    public String getWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Wiki page title (use 'Wiki' for the start page)") String pageTitle
    ) {
        var page = client.getWikiPage(projectId, pageTitle);
        if (page == null) {
            return errors.notFound("wiki page", pageTitle);
        }
        return json.write(page);
    }

    @McpTool(description = "List all wiki pages in a Redmine project. " +
            "Returns page titles and dates. Use getWikiPage to read a specific page's content.")
    public String listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var pages = client.getWikiIndex(projectId);
        return json.write(pages);
    }
}
