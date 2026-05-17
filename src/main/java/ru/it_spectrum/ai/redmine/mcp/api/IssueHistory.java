package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Interpreted history timeline for an issue: human-readable field changes plus aggregated time spent in each status.")
public record IssueHistory(
        @Schema(description = "Chronological timeline of creation and update events.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TimelineEntry> timeline,
        @Schema(description = "How long the issue stayed in each status, derived from journal entries.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<StatusDuration> statusDurations
) {

    @Schema(description = "Kind of timeline entry.")
    public enum Kind {
        @Schema(description = "Issue was created.", nullable = true) CREATED,
        @Schema(description = "Issue was updated (fields changed and/or a note was added).", nullable = true) UPDATED
    }

    @Schema(description = "Single timeline event — either issue creation or a later update.")
    public record TimelineEntry(
            @Schema(description = "Event kind.", requiredMode = Schema.RequiredMode.REQUIRED)
            Kind kind,
            @Schema(description = "Event timestamp in ISO-8601.", requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
            String timestamp,
            @Schema(description = "Name of the user who performed the action.", requiredMode = Schema.RequiredMode.REQUIRED)
            String actor,
            @Schema(description = "Field-level changes recorded with this entry; empty when the entry only carries a note.", requiredMode = Schema.RequiredMode.REQUIRED)
            List<FieldChange> changes,
            @Schema(description = "Free-text note attached to the entry, when present.", nullable = true)
            String note
    ) {
    }

    @Schema(description = "One field change with resolved (human-readable) old and new values.")
    public record FieldChange(
            @Schema(description = "Display label of the changed field (resolved custom-field name when applicable).", requiredMode = Schema.RequiredMode.REQUIRED)
            String fieldLabel,
            @Schema(description = "Previous value, null on creation.", nullable = true)
            String oldValue,
            @Schema(description = "New value, null on deletion.", nullable = true)
            String newValue
    ) {
    }

    @Schema(description = "Duration the issue spent in a particular status.")
    public record StatusDuration(
            @Schema(description = "Status name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String statusName,
            @Schema(description = "When the issue entered this status (ISO-8601).", requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
            String fromTimestamp,
            @Schema(description = "When the issue left this status; null when the issue is currently in this status.", format = "date-time", nullable = true)
            String toTimestamp,
            @Schema(description = "Human-readable duration (e.g. `3 days`, `< 1 hour`).", requiredMode = Schema.RequiredMode.REQUIRED, example = "3 days")
            String duration
    ) {
    }
}
