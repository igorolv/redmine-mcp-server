package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

import java.util.List;

@Schema(description = "Compact issue projection used in list and search results. Heavy fields (description, journals, attachments) and low-signal fields (author, isPrivate, spentHours) are omitted — fetch the full Issue when they are needed.")
public record IssueSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Project the issue belongs to.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref project,
        @Schema(description = "Tracker type (Bug, Feature, Task, ...).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref tracker,
        @Schema(description = "Current workflow status.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref status,
        @Schema(description = "Priority.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref priority,
        @Schema(description = "User currently assigned, null when unassigned.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref assignedTo,
        @Schema(description = "Parent issue reference if this is a subtask.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref parent,
        @Schema(description = "Target version / milestone.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref fixedVersion,
        @Schema(description = "Issue category.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref category,
        @Schema(description = "Short title of the issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String subject,
        @Schema(description = "Planned start date in ISO-8601.", format = "date", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String startDate,
        @Schema(description = "Planned due date in ISO-8601.", format = "date", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String dueDate,
        @Schema(description = "Completion percentage from 0 to 100.", requiredMode = Schema.RequiredMode.REQUIRED)
        int doneRatio,
        @Schema(description = "Estimated effort in hours.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Double estimatedHours,
        @Schema(description = "Creation timestamp in ISO-8601.", format = "date-time", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String createdOn,
        @Schema(description = "Timestamp of the most recent change in ISO-8601.", format = "date-time", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String updatedOn,
        @Schema(description = "Project-defined custom field values.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<CustomFieldValue> customFields
) {

    public static IssueSummary from(RedmineIssueSummary source) {
        if (source == null) {
            return null;
        }
        return new IssueSummary(
                source.id(),
                Ref.from(source.project()),
                Ref.from(source.tracker()),
                Ref.from(source.status()),
                Ref.from(source.priority()),
                Ref.from(source.assignedTo()),
                Ref.from(source.parent()),
                Ref.from(source.fixedVersion()),
                Ref.from(source.category()),
                source.subject(),
                source.startDate(),
                source.dueDate(),
                source.doneRatio(),
                source.estimatedHours(),
                source.createdOn(),
                source.updatedOn(),
                CustomFieldValue.fromAll(source.customFields())
        );
    }

    public static IssueSummary from(RedmineIssue source) {
        if (source == null) {
            return null;
        }
        return from(RedmineIssueSummary.fromIssue(source));
    }
}
