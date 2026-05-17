package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineQuery;

@Schema(description = "Saved Redmine query (custom filter). Apply with listIssues(queryId) — particularly useful for queries that target custom fields not exposed via dedicated parameters.")
public record Query(
        @Schema(description = "Query identifier. Pass as queryId to listIssues.", requiredMode = Schema.RequiredMode.REQUIRED, example = "17")
        int id,
        @Schema(description = "Display name shown in the Redmine UI.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Open bugs assigned to me", nullable = true)
        String name,
        @Schema(description = "True when the query is shared with other users; false when it is private to its owner.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isPublic,
        @Schema(description = "Project the query is scoped to. Null for cross-project queries.", example = "5", nullable = true)
        Integer projectId
) {
    public static Query from(RedmineQuery source) {
        if (source == null) {
            return null;
        }
        return new Query(source.id(), source.name(), source.isPublic(), source.projectId());
    }
}
