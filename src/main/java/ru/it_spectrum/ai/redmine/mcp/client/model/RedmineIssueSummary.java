package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineIssueSummary(
        int id,
        IdName project,
        IdName tracker,
        IdName status,
        IdName priority,
        IdName author,
        @JsonProperty("assigned_to") IdName assignedTo,
        IdName parent,
        @JsonProperty("fixed_version") IdName fixedVersion,
        IdName category,
        String subject,
        String description,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("done_ratio") int doneRatio,
        @JsonProperty("estimated_hours") Double estimatedHours,
        @JsonProperty("spent_hours") Double spentHours,
        @JsonProperty("is_private") boolean isPrivate,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        @JsonProperty("custom_fields") List<RedmineIssue.CustomField> customFields
) {

    public static RedmineIssueSummary fromIssue(RedmineIssue issue) {
        return new RedmineIssueSummary(
                issue.id(),
                issue.project(),
                issue.tracker(),
                issue.status(),
                issue.priority(),
                issue.author(),
                issue.assignedTo(),
                issue.parent(),
                issue.fixedVersion(),
                issue.category(),
                issue.subject(),
                issue.description(),
                issue.startDate(),
                issue.dueDate(),
                issue.doneRatio(),
                issue.estimatedHours(),
                issue.spentHours(),
                issue.isPrivate(),
                issue.createdOn(),
                issue.updatedOn(),
                issue.customFields()
        );
    }

    /** Wrapper for {"issues": [...], "total_count": N, "offset": N, "limit": N} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            List<RedmineIssueSummary> issues,
            @JsonProperty("total_count") int totalCount,
            int offset,
            int limit
    ) {
    }
}
