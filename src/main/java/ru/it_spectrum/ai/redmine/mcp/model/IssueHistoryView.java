package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record IssueHistoryView(
        RedmineIssue issue,
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
