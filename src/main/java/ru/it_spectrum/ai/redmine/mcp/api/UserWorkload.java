package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Workload analysis for a single user: open issue counts grouped by project and priority, overdue count, estimated vs spent hours and top-priority issues at the moment.")
public record UserWorkload(
        @Schema(description = "User identifier the analysis is for.", requiredMode = Schema.RequiredMode.REQUIRED)
        int userId,
        @Schema(description = "Display name of the user. May be a placeholder like 'User #42' when only an id was provided.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String userName,
        @Schema(description = "Project the workload was scoped to, null when scope is all projects.", nullable = true)
        String projectId,
        @Schema(description = "Total number of open issues assigned to the user across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalOpenIssues,
        @Schema(description = "Number of issues actually analyzed (may be smaller than totalOpenIssues when analysis was truncated).", requiredMode = Schema.RequiredMode.REQUIRED)
        int analyzedIssues,
        @Schema(description = "True when the analyzed set is a truncated slice of the full open set.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Estimated vs spent hours across the analyzed set.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        HoursSummary hours,
        @Schema(description = "Number of analyzed issues whose due date is in the past.", requiredMode = Schema.RequiredMode.REQUIRED)
        int overdue,
        @Schema(description = "Per-project breakdown of the user's workload.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<List<ProjectShare>> byProject,
        @Schema(description = "Up to 10 most important open issues for the user, sorted by priority then by due date.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<List<IssueSummary>> topIssues
) {

    @Schema(description = "Workload contribution from a single project.")
    public record ProjectShare(
            @Schema(description = "Project display name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String project,
            @Schema(description = "Number of analyzed issues in this project assigned to the user.", requiredMode = Schema.RequiredMode.REQUIRED)
            int issueCount,
            @Schema(description = "Sum of estimated hours across those issues that have an estimate.", requiredMode = Schema.RequiredMode.REQUIRED)
            double estimatedHours,
            @Schema(description = "Open-issue counts grouped by priority name within this project.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Map<String, Integer> byPriority
    ) {
    }
}
