package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Changelog for a Redmine version/milestone: all issues grouped by tracker, with completion counts and effort totals.")
public record VersionChangelog(
        @Schema(description = "Project identifier the version belongs to.", requiredMode = Schema.RequiredMode.REQUIRED)
        String projectId,
        @Schema(description = "Version/milestone identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionId,
        @Schema(description = "Version metadata, null when the version could not be resolved.", nullable = true)
        Version version,
        @Schema(description = "Total number of issues tied to this version across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalIssues,
        @Schema(description = "Number of issues actually analyzed for this changelog.", requiredMode = Schema.RequiredMode.REQUIRED)
        int analyzedIssues,
        @Schema(description = "True when the analyzed set is a truncated slice of the full issue set.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Issues grouped by tracker name (Bug, Feature, ...).", requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, List<IssueSummary>> byTracker,
        @Schema(description = "Open / closed / total counts within the analyzed set.", requiredMode = Schema.RequiredMode.REQUIRED)
        IssueCountSummary counts,
        @Schema(description = "Estimated vs spent hours across the analyzed set.", requiredMode = Schema.RequiredMode.REQUIRED)
        HoursSummary hours
) {
}
