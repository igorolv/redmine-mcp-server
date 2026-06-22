package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;

@Schema(description = "Single logged time entry in Redmine. Audit timestamps (created_on / updated_on) are omitted — `spentOn` is the date that matters for time-tracking analysis.")
public record TimeEntry(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Project the time was logged against.", nullable = true)
        Ref project,
        @Schema(description = "Issue the time was logged against, null when the entry is attached only to the project.", nullable = true)
        Ref issue,
        @Schema(description = "User who logged the time.", nullable = true)
        Ref user,
        @Schema(description = "Activity classification (Development, Testing, Design, ...).", nullable = true)
        Ref activity,
        @Schema(description = "Logged hours.", requiredMode = Schema.RequiredMode.REQUIRED)
        double hours,
        @Schema(description = "Optional comment supplied with the time entry.", nullable = true)
        String comments,
        @Schema(description = "Date the work was performed, ISO-8601 (yyyy-MM-dd).", format = "date", nullable = true)
        String spentOn
) {
    public static TimeEntry from(RedmineTimeEntry source) {
        if (source == null) {
            return null;
        }
        return new TimeEntry(
                source.id(),
                Ref.from(source.project()),
                Ref.from(source.issue()),
                Ref.from(source.user()),
                Ref.from(source.activity()),
                source.hours(),
                source.comments(),
                source.spentOn()
        );
    }
}
