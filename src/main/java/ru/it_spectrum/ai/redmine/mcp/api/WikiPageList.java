package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "List of wiki pages in a Redmine project. Wraps a JSON array so the MCP outputSchema can be an object.")
public record WikiPageList(
        @Schema(description = "Wiki pages.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<WikiPage> pages
) {
    public static WikiPageList of(List<WikiPage> pages) {
        return new WikiPageList(pages == null ? List.of() : pages);
    }
}
