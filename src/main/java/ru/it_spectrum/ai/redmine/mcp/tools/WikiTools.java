package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class WikiTools {
    private final RedmineClient client;

    public WikiTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "Get a wiki page from a Redmine project. " +
            "Returns the page title, content (in Textile/Markdown markup), author, and attachments.")
    public String getWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Wiki page title (use 'Wiki' for the start page)") String pageTitle
    ) {
        var page = client.getWikiPage(projectId, pageTitle);
        if (page == null) {
            return "Wiki page '%s' not found in project '%s'".formatted(pageTitle, projectId);
        }

        var sb = new StringBuilder();
        sb.append("Wiki: %s\n".formatted(page.title()));
        if (page.author() != null) sb.append("Author: %s\n".formatted(page.author().name()));
        sb.append("Version: %d | Updated: %s\n".formatted(page.version(), page.updatedOn()));

        if (page.text() != null) {
            sb.append("\n--- Content ---\n%s\n".formatted(page.text()));
        }

        if (page.attachments() != null && !page.attachments().isEmpty()) {
            sb.append("\nAttachments (%d):\n".formatted(page.attachments().size()));
            for (var att : page.attachments()) {
                sb.append("  - [%d] %s (%s)\n".formatted(att.id(), att.filename(), formatSize(att.filesize())));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "List all wiki pages in a Redmine project. " +
            "Returns page titles and dates. Use getWikiPage to read a specific page's content.")
    public String listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var pages = client.getWikiIndex(projectId);
        if (pages.isEmpty()) {
            return "No wiki pages found in project '%s'".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Wiki pages in project '%s' (%d):\n\n".formatted(projectId, pages.size()));
        for (var page : pages) {
            sb.append("- %s".formatted(page.title()));
            if (page.updatedOn() != null) sb.append(" (updated: %s)".formatted(page.updatedOn()));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
