package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Open issues that have not been updated for at least `daysSinceUpdate` days, sorted by staleness (oldest first).")
public record StaleIssues(
        @Schema(description = "Project identifier the search was scoped to.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String projectId,
        @Schema(description = "Inactivity threshold in days that was applied.", requiredMode = Schema.RequiredMode.REQUIRED)
        int daysSinceUpdate,
        @Schema(description = "Maximum number of results the search was capped at.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(description = "Stale issues that crossed the threshold.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Entry> issues,
        @Schema(description = "How many days the oldest returned issue has been untouched. 0 when the result set is empty.", requiredMode = Schema.RequiredMode.REQUIRED)
        long oldestDaysSinceUpdated
) {

    @Schema(description = "Single stale issue with how long it has been since its last update.")
    public record Entry(
            @Schema(description = "The stale issue.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            IssueSummary issue,
            @Schema(description = "Days since the issue was last updated.", requiredMode = Schema.RequiredMode.REQUIRED)
            long daysSinceUpdated,
            @Schema(description = "True when the issue is also past its due date.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean overdue
    ) {
    }
}
