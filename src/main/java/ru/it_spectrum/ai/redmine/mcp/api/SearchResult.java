package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult;

import java.util.List;

@Schema(description = "Paginated slice of Redmine full-text search hits. Hits may be issues, wiki pages, news, documents, projects, etc. — distinguished by `type` on each hit.")
public record SearchResult(
        @Schema(description = "Hits on this page.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Hit> results,
        @Schema(description = "Total number of hits across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(description = "Zero-based offset of the first hit on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Maximum number of hits that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit
) {
    public static SearchResult from(RedmineSearchResult source) {
        if (source == null) {
            return null;
        }
        var items = source.results() == null
                ? List.<Hit>of()
                : source.results().stream().map(Hit::from).toList();
        return new SearchResult(items, source.totalCount(), source.offset(), source.limit());
    }

    @Schema(description = "Single search hit. The `type` discriminates what kind of Redmine entity this is.")
    public record Hit(
            @Schema(description = "Identifier of the matched entity within its type's namespace (issue id, wiki revision id, etc.).", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Title or subject as shown in search results.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String title,
            @Schema(description = "Kind of entity that matched.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"issue", "issue-closed", "wiki-page", "news", "document", "changeset", "message", "project"}, nullable = true)
            String type,
            @Schema(description = "Direct link to the entity in the Redmine UI.", nullable = true)
            String url,
            @Schema(description = "Snippet around the match, may be truncated.", nullable = true)
            String description,
            @Schema(description = "Timestamp of the matched entity in ISO-8601.", format = "date-time", nullable = true)
            String datetime
    ) {
        public static Hit from(RedmineSearchResult.ResultItem source) {
            if (source == null) {
                return null;
            }
            return new Hit(source.id(), source.title(), source.type(),
                    source.url(), source.description(), source.datetime());
        }
    }
}
