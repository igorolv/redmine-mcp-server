package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

@Schema(description = "Full Redmine issue with description, relations, history notes, child tasks and attachments.")
public record Issue(
        @Schema(description = "Issue identifier (the # shown in Redmine).", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345")
        int id,
        @Schema(description = "Project the issue belongs to.", requiredMode = Schema.RequiredMode.REQUIRED)
        Ref project,
        @Schema(description = "Tracker type (Bug, Feature, Task, ...).", requiredMode = Schema.RequiredMode.REQUIRED)
        Ref tracker,
        @Schema(description = "Current workflow status (e.g. New, In Progress, Closed).", requiredMode = Schema.RequiredMode.REQUIRED)
        Ref status,
        @Schema(description = "Priority (e.g. Normal, High, Urgent).", requiredMode = Schema.RequiredMode.REQUIRED)
        Ref priority,
        @Schema(description = "Author who created the issue.")
        Ref author,
        @Schema(description = "User currently assigned to the issue; null when unassigned.")
        Ref assignedTo,
        @Schema(description = "Parent issue reference if this is a sub-task; null for root-level issues.")
        Ref parent,
        @Schema(description = "Target version / milestone this issue is planned for.")
        Ref fixedVersion,
        @Schema(description = "Issue category within the project.")
        Ref category,
        @Schema(description = "Short title of the issue.", requiredMode = Schema.RequiredMode.REQUIRED, example = "API returns 500 on empty payload")
        String subject,
        @Schema(description = "Long-form description of the issue, may contain Textile or Markdown markup depending on the Redmine instance.")
        String description,
        @Schema(description = "Planned start date in ISO-8601 (yyyy-MM-dd).", format = "date", example = "2025-04-01")
        String startDate,
        @Schema(description = "Planned due date in ISO-8601 (yyyy-MM-dd).", format = "date", example = "2025-04-15")
        String dueDate,
        @Schema(description = "Completion percentage from 0 to 100.", requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
        int doneRatio,
        @Schema(description = "Estimated effort in hours.", example = "8.0")
        Double estimatedHours,
        @Schema(description = "Aggregated time already logged against the issue, in hours.", example = "3.5")
        Double spentHours,
        @Schema(description = "Creation timestamp in ISO-8601.", format = "date-time", example = "2024-12-31T10:15:30Z")
        String createdOn,
        @Schema(description = "Timestamp of the most recent change in ISO-8601.", format = "date-time", example = "2025-01-15T09:00:00Z")
        String updatedOn,
        @Schema(description = "Project-defined custom field values, in display form.")
        List<CustomFieldValue> customFields,
        @Schema(description = "Files attached to the issue.")
        List<Attachment> attachments,
        @Schema(description = "Chronological history entries — notes, status changes, field edits.")
        List<Journal> journals,
        @Schema(description = "Cross-issue relations (blocks, duplicates, relates, ...).")
        List<Relation> relations,
        @Schema(description = "Direct child issues (subtasks).")
        List<Child> children,
        @Schema(description = "Linked VCS changesets/commits, when the Redmine repository integration exposes them.")
        List<Changeset> changesets
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
                Ref.from(source.parent()),
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
                mapList(source.attachments(), Attachment::from),
                Journal.fromAll(source.journals()),
                Relation.fromAll(source.relations()),
                Child.fromAll(source.children()),
                Changeset.fromAll(source.changesets())
        );
    }

    private static <S, T> List<T> mapList(List<S> source, java.util.function.Function<S, T> mapper) {
        if (source == null) {
            return null;
        }
        return source.stream().map(mapper).toList();
    }

    @Schema(description = "Project-defined custom field value in display form.")
    public record CustomFieldValue(
            @Schema(description = "Custom field name as configured in the project.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Customer code")
            String name,
            @Schema(description = "Effective values (single-valued fields produce one element).", requiredMode = Schema.RequiredMode.REQUIRED)
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
            return source.stream().map(CustomFieldValue::from).toList();
        }
    }

    @Schema(description = "Reference to a direct subtask of this issue (lightweight, fetch the issue separately for full details).")
    public record Child(
            @Schema(description = "Child issue identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Tracker of the child issue.")
            Ref tracker,
            @Schema(description = "Child issue subject.", requiredMode = Schema.RequiredMode.REQUIRED)
            String subject
    ) {
        public static Child from(RedmineIssue.Child source) {
            if (source == null) {
                return null;
            }
            return new Child(source.id(), Ref.from(source.tracker()), source.subject());
        }

        public static List<Child> fromAll(List<RedmineIssue.Child> source) {
            if (source == null) {
                return null;
            }
            return source.stream().map(Child::from).toList();
        }
    }

    @Schema(description = "Cross-issue relation. `issueId` is the source side, `issueToId` is the target side; `relationType` describes the link.")
    public record Relation(
            @Schema(description = "Relation identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Source issue id of the relation.", requiredMode = Schema.RequiredMode.REQUIRED)
            int issueId,
            @Schema(description = "Target issue id of the relation.", requiredMode = Schema.RequiredMode.REQUIRED)
            int issueToId,
            @Schema(description = "Type of relation.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"relates", "duplicates", "duplicated", "blocks", "blocked", "precedes", "follows", "copied_to", "copied_from"})
            String relationType,
            @Schema(description = "Delay in days, used by precedes/follows relations.")
            Integer delay
    ) {
        public static Relation from(RedmineIssue.Relation source) {
            if (source == null) {
                return null;
            }
            return new Relation(source.id(), source.issueId(), source.issueToId(),
                    source.relationType(), source.delay());
        }

        public static List<Relation> fromAll(List<RedmineIssue.Relation> source) {
            if (source == null) {
                return null;
            }
            return source.stream().map(Relation::from).toList();
        }
    }

    @Schema(description = "Single history entry: a user comment, a set of field changes, or both.")
    public record Journal(
            @Schema(description = "Journal entry identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Author of the change.")
            Ref user,
            @Schema(description = "Free-text note. Empty when the entry only carries field changes.")
            String notes,
            @Schema(description = "Timestamp the entry was recorded, ISO-8601.", format = "date-time")
            String createdOn,
            @Schema(description = "Field-level changes recorded in this entry. Raw form — use issue history endpoints for resolved values.")
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
            return source.stream().map(Journal::from).toList();
        }
    }

    @Schema(description = "Single field-level change inside a journal entry. Values are raw Redmine identifiers when the property is `attr` (e.g. status_id), or raw text otherwise.")
    public record Detail(
            @Schema(description = "Property kind.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"attr", "cf", "relation", "attachment"})
            String property,
            @Schema(description = "Property name (e.g. status_id, subject) when property is `attr`; custom field id when property is `cf`.", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Previous raw value, null on creation.")
            String oldValue,
            @Schema(description = "New raw value, null on deletion.")
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
            return source.stream().map(Detail::from).toList();
        }
    }

    @Schema(description = "Linked VCS commit / changeset associated with the issue.")
    public record Changeset(
            @Schema(description = "Revision identifier as reported by the VCS.", requiredMode = Schema.RequiredMode.REQUIRED)
            String revision,
            @Schema(description = "Committer / author of the changeset, when Redmine maps it to a user.")
            Ref user,
            @Schema(description = "Commit message.")
            String comments,
            @Schema(description = "Commit timestamp in ISO-8601.", format = "date-time")
            String committedOn
    ) {
        public static Changeset from(RedmineIssue.Changeset source) {
            if (source == null) {
                return null;
            }
            return new Changeset(source.revision(), Ref.from(source.user()),
                    source.comments(), source.committedOn());
        }

        public static List<Changeset> fromAll(List<RedmineIssue.Changeset> source) {
            if (source == null) {
                return null;
            }
            return source.stream().map(Changeset::from).toList();
        }
    }
}
