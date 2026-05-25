package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Aggregated snapshot of a Redmine project (optionally scoped to a version): issue counts by state, distribution across statuses/trackers/priorities/assignees on the analyzed open set, overdue count and effort totals.")
public record ProjectSummary(
        @Schema(description = "Project identifier the summary was computed for.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String projectId,
        @Schema(description = "Version/milestone the summary was scoped to, null when unscoped.", nullable = true)
        Integer versionId,
        @Schema(description = "Open / closed / total issue counts for the scope.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        IssueCountSummary counts,
        @Schema(description = "True when the analyzed open issue set is a truncated slice of the full open set (analysis cap reached).", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Number of open issues actually analyzed for the distributions below.", requiredMode = Schema.RequiredMode.REQUIRED)
        int analyzedOpenIssues,
        @Schema(description = "Open-issue counts grouped by status name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Map<String, Integer> byStatus,
        @Schema(description = "Open-issue counts grouped by tracker name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Map<String, Integer> byTracker,
        @Schema(description = "Open-issue counts grouped by priority name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Map<String, Integer> byPriority,
        @Schema(description = "Per-assignee workload — total and overdue counts.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Assignee> byAssignee,
        @Schema(description = "Number of open issues whose due date is in the past.", requiredMode = Schema.RequiredMode.REQUIRED)
        int overdue,
        @Schema(description = "Estimated vs spent hours for the analyzed open set.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        HoursSummary hours
) {

    @Schema(description = "Per-assignee workload entry.")
    public record Assignee(
            @Schema(description = "Display name of the assignee, or 'Unassigned' bucket.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String assignee,
            @Schema(description = "Number of open issues assigned to this user.", requiredMode = Schema.RequiredMode.REQUIRED)
            int total,
            @Schema(description = "Number of those issues that are overdue.", requiredMode = Schema.RequiredMode.REQUIRED)
            int overdue
    ) {
    }
}
