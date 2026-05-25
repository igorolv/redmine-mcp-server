package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.CommitReferenceExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "Full Redmine issue with description, relations, history notes, child tasks and attachments.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Issue(
        @Schema(description = "Issue identifier (the # shown in Redmine).", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345")
        int id,
        @Schema(description = "Project the issue belongs to.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref project,
        @Schema(description = "Tracker type (Bug, Feature, Task, ...).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref tracker,
        @Schema(description = "Current workflow status (e.g. New, In Progress, Closed).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref status,
        @Schema(description = "Priority (e.g. Normal, High, Urgent).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref priority,
        @Schema(description = "Author who created the issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref author,
        @Schema(description = "User currently assigned to the issue; null when unassigned.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref assignedTo,
        @Schema(description = "Target version / milestone this issue is planned for.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref fixedVersion,
        @Schema(description = "Issue category within the project.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref category,
        @Schema(description = "Short title of the issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "API returns 500 on empty payload", nullable = true)
        String subject,
        @Schema(description = "Long-form description of the issue, may contain Textile or Markdown markup depending on the Redmine instance.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String description,
        @Schema(description = "Planned start date in ISO-8601 (yyyy-MM-dd).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date", example = "2025-04-01", nullable = true)
        String startDate,
        @Schema(description = "Planned due date in ISO-8601 (yyyy-MM-dd).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date", example = "2025-04-15", nullable = true)
        String dueDate,
        @Schema(description = "Completion percentage from 0 to 100.", requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
        int doneRatio,
        @Schema(description = "Estimated effort in hours.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "8.0", nullable = true)
        Double estimatedHours,
        @Schema(description = "Aggregated time already logged against the issue, in hours.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "3.5", nullable = true)
        Double spentHours,
        @Schema(description = "Creation timestamp in ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", example = "2024-12-31T10:15:30Z", nullable = true)
        String createdOn,
        @Schema(description = "Timestamp of the most recent change in ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", example = "2025-01-15T09:00:00Z", nullable = true)
        String updatedOn,
        @Schema(description = "Project-defined custom field values, in display form.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<CustomFieldValue> customFields,
        @Schema(description = "Files attached to the issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Attachment> attachments,
        @Schema(description = "Chronological history entries — notes, status changes, field edits.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Journal> journals,
        @Schema(description = "Enriched references to related issues (parent, siblings, children, relations) — id + subject/tracker/status + roles. Populated when the issue is loaded with related-issue enrichment; null otherwise. Use it to decide which related issues are worth fetching in full.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<RelatedRef> related,
        @Schema(description = "Linked VCS changesets/commits, when the Redmine repository integration exposes them.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Changeset> changesets,
        @Schema(description = "Human-readable notes describing semantic focus shaping applied before response-size compression. Null/empty when no focus shaping was applied.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<String> focusNotes,
        @Schema(description = "Human-readable notes describing how this response was compressed to fit the response size budget. Null/empty when no compression was applied.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<String> compressionNotes
) {

    public static Issue from(RedmineIssue source) {
        if (source == null) {
            return null;
        }
        return new Issue(
                source.id(),
                Ref.from(source.project()),
                Ref.from(source.tracker()),
                Ref.from(source.status()),
                Ref.from(source.priority()),
                Ref.from(source.author()),
                Ref.from(source.assignedTo()),
                Ref.from(source.fixedVersion()),
                Ref.from(source.category()),
                source.subject(),
                source.description(),
                source.startDate(),
                source.dueDate(),
                source.doneRatio(),
                source.estimatedHours(),
                source.spentHours(),
                source.createdOn(),
                source.updatedOn(),
                CustomFieldValue.fromAll(source.customFields()),
                ApiCollections.mapNonNull(source.attachments(), Attachment::from),
                Journal.fromAll(source.journals()),
                null,
                mergeChangesets(source),
                null,
                null
        );
    }

    public Issue withChangesets(List<Changeset> newChangesets) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, related, newChangesets, focusNotes, compressionNotes);
    }

    public Issue withJournals(List<Journal> newJournals) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                newJournals, related, changesets, focusNotes, compressionNotes);
    }

    public Issue withRelated(List<RelatedRef> newRelated) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, newRelated, changesets, focusNotes, compressionNotes);
    }

    public Issue withCustomFields(List<CustomFieldValue> newCustomFields) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, newCustomFields, attachments,
                journals, related, changesets, focusNotes, compressionNotes);
    }

    public Issue withAttachments(List<Attachment> newAttachments) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, newAttachments,
                journals, related, changesets, focusNotes, compressionNotes);
    }

    public Issue withFocusNotes(List<String> newFocusNotes) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, related, changesets, newFocusNotes, compressionNotes);
    }

    public Issue withCompressionNotes(List<String> newCompressionNotes) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, related, changesets, focusNotes, newCompressionNotes);
    }

    private static List<Changeset> mergeChangesets(RedmineIssue source) {
        var fromRedmine = Changeset.fromAll(source.changesets());
        var fromNotes = CommitReferenceExtractor.extractFromJournals(source.journals(), fromRedmine);
        if (fromNotes.isEmpty()) {
            return fromRedmine;
        }
        var combined = new ArrayList<Changeset>();
        if (fromRedmine != null) {
            combined.addAll(fromRedmine);
        }
        combined.addAll(fromNotes);
        return List.copyOf(combined);
    }

}
