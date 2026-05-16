package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.List;

public record IssueHistoryView(
        List<TimelineEntry> timeline,
        List<StatusDuration> statusDurations
) {

    public enum Kind {CREATED, UPDATED}

    public record TimelineEntry(
            Kind kind,
            String timestamp,
            String actor,
            List<FieldChange> changes,
            String note
    ) {
    }

    public record FieldChange(
            String fieldLabel,
            String oldValue,
            String newValue
    ) {
    }

    public record StatusDuration(
            String statusName,
            String fromTimestamp,
            String toTimestamp,
            String duration
    ) {
    }
}
