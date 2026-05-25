package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineQuery;

import java.util.List;

@Schema(description = "Paginated slice of saved queries.")
public record QueryPage(
        @Schema(description = "Queries on this page.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Query> queries,
        @Schema(description = "Total number of queries across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(description = "Zero-based offset of the first query on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Maximum number of queries that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit
) {
    public static QueryPage from(RedmineQuery.Page source) {
        if (source == null) {
            return null;
        }
        var items = source.queries() == null
                ? List.<Query>of()
                : source.queries().stream().map(Query::from).toList();
        return new QueryPage(items, source.totalCount(), source.offset(), source.limit());
    }
}
