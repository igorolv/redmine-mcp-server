package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Side-by-side comparison of two Redmine versions/milestones: per-version completion stats plus issue diffs (only-in-first, only-in-second, shared).")
public record VersionComparison(
        @Schema(description = "Project identifier the versions belong to.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String projectId,
        @Schema(description = "Scope statistics for the first version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Scope first,
        @Schema(description = "Scope statistics for the second version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Scope second,
        @Schema(description = "Issues present only in the first version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<IssueSummary>> onlyInFirst,
        @Schema(description = "Issues present only in the second version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<IssueSummary>> onlyInSecond,
        @Schema(description = "Issues present in both versions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<IssueSummary>> inBoth
) {

    @Schema(description = "Per-version scope statistics.")
    public record Scope(
            @Schema(description = "Version identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int versionId,
            @Schema(description = "Version name (or `#id` placeholder when the version could not be resolved).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String name,
            @Schema(description = "Full version metadata, null when the version could not be resolved.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Version version,
            @Schema(description = "Total number of issues tied to this version across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
            int totalIssues,
            @Schema(description = "Number of issues actually analyzed.", requiredMode = Schema.RequiredMode.REQUIRED)
            int analyzedIssues,
            @Schema(description = "Closed issues within the analyzed set.", requiredMode = Schema.RequiredMode.REQUIRED)
            int closed,
            @Schema(description = "Open issues within the analyzed set.", requiredMode = Schema.RequiredMode.REQUIRED)
            int open,
            @Schema(description = "Completion percentage (closed / analyzedIssues, rounded).", requiredMode = Schema.RequiredMode.REQUIRED)
            int completionPercent,
            @Schema(description = "True when the analyzed set is a truncated slice of the full issue set.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean truncated
    ) {
    }
}
