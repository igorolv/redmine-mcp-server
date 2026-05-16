package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

@Schema(description = "Risk assessment for a Redmine version/milestone: open issues classified into blockers, overdue, high-priority and unassigned categories, plus an aggregate score.")
public record ReleaseRisks(
        @Schema(description = "Project identifier the version belongs to.", requiredMode = Schema.RequiredMode.REQUIRED)
        String projectId,
        @Schema(description = "Version/milestone identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionId,
        @Schema(description = "Version metadata, null when the version could not be resolved.")
        Version version,
        @Schema(description = "Total open issues for the version across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalOpenIssues,
        @Schema(description = "Number of issues actually analyzed for risks.", requiredMode = Schema.RequiredMode.REQUIRED)
        int analyzedIssues,
        @Schema(description = "True when the analyzed set is a truncated slice of the full open set.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Priority names treated as 'high' by the heuristic (top third of the priority ladder, or just the highest one).", requiredMode = Schema.RequiredMode.REQUIRED)
        Set<String> highPriorityNames,
        @Schema(description = "Risk categories that contain at least one issue.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Category> categories,
        @Schema(description = "Aggregate risk score.", requiredMode = Schema.RequiredMode.REQUIRED)
        Score score
) {

    @Schema(description = "Group of issues that all share a particular risk characteristic.")
    public record Category(
            @Schema(description = "Risk kind.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"blockers", "overdue", "high_priority", "unassigned"})
            String kind,
            @Schema(description = "Issues in this category.", requiredMode = Schema.RequiredMode.REQUIRED)
            List<IssueSummary> issues
    ) {
    }

    @Schema(description = "Aggregate risk score.")
    public record Score(
            @Schema(description = "Total number of issues across all risk categories (an issue may be counted in multiple categories).", requiredMode = Schema.RequiredMode.REQUIRED)
            int items,
            @Schema(description = "Number of non-empty risk categories.", requiredMode = Schema.RequiredMode.REQUIRED)
            int categories,
            @Schema(description = "Total number of analyzed open issues — the denominator for risk severity.", requiredMode = Schema.RequiredMode.REQUIRED)
            int openIssues
    ) {
    }
}
