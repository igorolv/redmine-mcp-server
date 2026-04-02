package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineIssue(
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
        @JsonProperty("custom_fields") List<CustomField> customFields,
        List<RedmineAttachment> attachments,
        List<Journal> journals,
        List<Relation> relations,
        List<Child> children
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Child(
            int id,
            IdName tracker,
            String subject
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomField(
            int id,
            String name,
            Object value
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relation(
            int id,
            @JsonProperty("issue_id") int issueId,
            @JsonProperty("issue_to_id") int issueToId,
            @JsonProperty("relation_type") String relationType,
            Integer delay
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Journal(
            int id,
            IdName user,
            String notes,
            @JsonProperty("created_on") String createdOn
    ) {
    }

    /** Wrapper for {"issue": {...}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(RedmineIssue issue) {
    }

    /** Wrapper for {"issues": [...], "total_count": N, "offset": N, "limit": N} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            List<RedmineIssue> issues,
            @JsonProperty("total_count") int totalCount,
            int offset,
            int limit
    ) {
    }
}
