package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;

@Schema(description = "Single logged time entry in Redmine. Audit timestamps (created_on / updated_on) are omitted — `spentOn` is the date that matters for time-tracking analysis.")
public record TimeEntry(
        @Schema(description = "Time entry identifier.", requiredMode = Schema.RequiredMode.REQUIRED, example = "9876")
        int id,
        @Schema(description = "Project the time was logged against.")
        Ref project,
        @Schema(description = "Issue the time was logged against, null when the entry is attached only to the project.")
        Ref issue,
        @Schema(description = "User who logged the time.")
        Ref user,
        @Schema(description = "Activity classification (Development, Testing, Design, ...).")
        Ref activity,
        @Schema(description = "Logged hours.", requiredMode = Schema.RequiredMode.REQUIRED, example = "2.5")
        double hours,
        @Schema(description = "Optional comment supplied with the time entry.")
        String comments,
        @Schema(description = "Date the work was performed, ISO-8601 (yyyy-MM-dd).", format = "date", example = "2025-03-15")
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
