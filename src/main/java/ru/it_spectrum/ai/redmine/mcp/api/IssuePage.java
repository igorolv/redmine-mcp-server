package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

import java.util.List;

@Schema(description = "Paginated slice of issue summaries. Use `offset` + `limit` to walk the full result set; `totalCount` tells you when to stop.")
public record IssuePage(
        @Schema(description = "Issues on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<IssueSummary> issues,
        @Schema(description = "Total number of issues matching the query across all pages.", requiredMode = Schema.RequiredMode.REQUIRED, example = "137")
        int totalCount,
        @Schema(description = "Zero-based offset of the first issue on this page.", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        int offset,
        @Schema(description = "Maximum number of issues that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED, example = "25")
        int limit
) {
    public static IssuePage from(RedmineIssueSummary.Page page) {
        if (page == null) {
            return null;
        }
        return new IssuePage(
                mapSummaries(page.issues()),
                page.totalCount(),
                page.offset(),
                page.limit()
        );
    }

    public static IssuePage from(RedmineClient.SearchWithIssueSummaries result) {
        if (result == null) {
            return null;
        }
        return new IssuePage(
                mapSummaries(result.issues()),
                result.totalCount(),
                result.offset(),
                result.limit()
        );
    }

    private static List<IssueSummary> mapSummaries(List<RedmineIssueSummary> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().map(IssueSummary::from).toList();
    }
}
