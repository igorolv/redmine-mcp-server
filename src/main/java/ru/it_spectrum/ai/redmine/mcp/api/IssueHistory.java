package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Interpreted history timeline for an issue: human-readable field changes plus aggregated time spent in each status.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueHistory(
        @Schema(description = "Chronological timeline of creation and update events.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<TimelineEntry> timeline,
        @Schema(description = "How long the issue stayed in each status, derived from journal entries.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<StatusDuration> statusDurations
) {

    @Schema(description = "Kind of timeline entry.")
    public enum Kind {
        @Schema(description = "Issue was created.", nullable = true) CREATED,
        @Schema(description = "Issue was updated (fields changed and/or a note was added).", nullable = true) UPDATED
    }

    @Schema(description = "Single timeline event — either issue creation or a later update.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(
            @Schema(description = "Event kind.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Kind kind,
            @Schema(description = "Event timestamp in ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String timestamp,
            @Schema(description = "Name of the user who performed the action.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String actor,
            @Schema(description = "Field-level changes recorded with this entry; empty when the entry only carries a note.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            List<FieldChange> changes,
            @Schema(description = "Free-text note attached to the entry, when present.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String note
    ) {
    }

    @Schema(description = "One field change with resolved (human-readable) old and new values.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldChange(
            @Schema(description = "Display label of the changed field (resolved custom-field name when applicable).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String fieldLabel,
            @Schema(description = "Previous value, null on creation.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String oldValue,
            @Schema(description = "New value, null on deletion.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String newValue
    ) {
    }

    @Schema(description = "Duration the issue spent in a particular status.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusDuration(
            @Schema(description = "Status name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String statusName,
            @Schema(description = "When the issue entered this status (ISO-8601).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String fromTimestamp,
            @Schema(description = "When the issue left this status; null when the issue is currently in this status.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String toTimestamp,
            @Schema(description = "Human-readable duration (e.g. `3 days`, `< 1 hour`).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String duration
    ) {
    }
}
