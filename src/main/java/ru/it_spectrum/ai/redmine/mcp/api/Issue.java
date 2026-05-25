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
                null
        );
    }

    public Issue withChangesets(List<Changeset> newChangesets) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, related, newChangesets, compressionNotes);
    }

    public Issue withJournals(List<Journal> newJournals) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                newJournals, related, changesets, compressionNotes);
    }

    public Issue withRelated(List<RelatedRef> newRelated) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, newRelated, changesets, compressionNotes);
    }

    public Issue withCompressionNotes(List<String> newCompressionNotes) {
        return new Issue(id, project, tracker, status, priority, author, assignedTo,
                fixedVersion, category, subject, description, startDate, dueDate, doneRatio,
                estimatedHours, spentHours, createdOn, updatedOn, customFields, attachments,
                journals, related, changesets, newCompressionNotes);
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

    @Schema(description = "Project-defined custom field value in display form.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomFieldValue(
            @Schema(description = "Custom field name as configured in the project.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "Customer code", nullable = true)
            String name,
            @Schema(description = "Effective values (single-valued fields produce one element).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            List<String> values
    ) {
        public static CustomFieldValue from(RedmineIssue.CustomField source) {
            if (source == null) {
                return null;
            }
            return new CustomFieldValue(source.name(), source.values());
        }

        public static List<CustomFieldValue> fromAll(List<RedmineIssue.CustomField> source) {
            if (source == null) {
                return null;
            }
            return ApiCollections.mapNonNull(source, CustomFieldValue::from);
        }
    }

    @Schema(description = "Single history entry: a user comment, a set of field changes, or both.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Journal(
            @Schema(description = "Journal entry identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Author of the change.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Ref user,
            @Schema(description = "Free-text note. Empty when the entry only carries field changes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String notes,
            @Schema(description = "Timestamp the entry was recorded, ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String createdOn,
            @Schema(description = "Field-level changes recorded in this entry. Raw form — use issue history endpoints for resolved values.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            List<Detail> details
    ) {
        public static Journal from(RedmineIssue.Journal source) {
            if (source == null) {
                return null;
            }
            return new Journal(source.id(), Ref.from(source.user()), source.notes(),
                    source.createdOn(), Detail.fromAll(source.details()));
        }

        public static List<Journal> fromAll(List<RedmineIssue.Journal> source) {
            if (source == null) {
                return null;
            }
            return ApiCollections.mapNonNull(source, Journal::from);
        }
    }

    @Schema(description = "Single field-level change inside a journal entry. Values are raw Redmine identifiers when the property is `attr` (e.g. status_id), or raw text otherwise.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            @Schema(description = "Property kind.", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allowableValues = {"attr", "cf", "relation", "attachment"}, nullable = true)
            String property,
            @Schema(description = "Property name (e.g. status_id, subject) when property is `attr`; custom field id when property is `cf`.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String name,
            @Schema(description = "Previous raw value, null on creation.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String oldValue,
            @Schema(description = "New raw value, null on deletion.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String newValue
    ) {
        public static Detail from(RedmineIssue.Detail source) {
            if (source == null) {
                return null;
            }
            return new Detail(source.property(), source.name(), source.oldValue(), source.newValue());
        }

        public static List<Detail> fromAll(List<RedmineIssue.Detail> source) {
            if (source == null) {
                return null;
            }
            return ApiCollections.mapNonNull(source, Detail::from);
        }
    }

    @Schema(description = "Linked VCS commit / changeset associated with the issue. May be sourced from Redmine's repository integration or extracted from journal note URLs.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Changeset(
            @Schema(description = "Revision identifier as reported by the VCS. Lowercased when extracted from journal URLs (may be a short prefix).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String revision,
            @Schema(description = "Committer / author of the changeset. For `redmine`-sourced entries this is the VCS user mapping; for `comment_reference` entries this is the journal author.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Ref user,
            @Schema(description = "Commit message. Null for `comment_reference` entries — we don't try to parse free-form notes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String comments,
            @Schema(description = "Commit timestamp for `redmine`-sourced entries; journal creation time for `comment_reference` entries. ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
            String committedOn,
            @Schema(description = "Where the reference came from: `redmine` — from Redmine's repository integration; `comment_reference` — extracted from a commit URL inside a journal note.",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    allowableValues = {"redmine", "comment_reference"}, nullable = true)
            String source
    ) {
        public static final String SOURCE_REDMINE = "redmine";
        public static final String SOURCE_COMMENT_REFERENCE = "comment_reference";

        public static Changeset from(RedmineIssue.Changeset source) {
            if (source == null) {
                return null;
            }
            return new Changeset(source.revision(), Ref.from(source.user()),
                    source.comments(), source.committedOn(), SOURCE_REDMINE);
        }

        public static List<Changeset> fromAll(List<RedmineIssue.Changeset> source) {
            if (source == null) {
                return null;
            }
            return ApiCollections.mapNonNull(source, Changeset::from);
        }
    }
}
