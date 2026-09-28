package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Interpreted issue history. Read pages until nextOffset is absent to cover every event.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueHistory(
        @Schema(description = "Chronological timeline of creation and update events.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<TimelineEntry>> timeline,
        @Schema(description = "Status intervals beginning in this page's events.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<StatusDuration>> statusDurations,
        @Schema(description = "Index of this page's first event.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Total creation and journal events available.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalEvents,
        @Schema(description = "Pass this as offset to getIssueHistory for the next page; absent when complete.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer nextOffset,
        @Schema(description = "Issue update timestamp; compare across pages to detect intervening edits.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String sourceUpdatedOn,
        @Schema(description = "What text was shortened and how to retrieve it in full.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<String> compressionNotes
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
            @Schema(description = "Redmine journal ID for updates; absent for creation. Use with getIssueJournal for full text.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Integer journalId,
            @Schema(description = "Event timestamp in ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String timestamp,
            @Schema(description = "Name of the user who performed the action.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String actor,
            @Schema(description = "Field-level changes; may be empty for a note-only or otherwise detail-free journal.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
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
