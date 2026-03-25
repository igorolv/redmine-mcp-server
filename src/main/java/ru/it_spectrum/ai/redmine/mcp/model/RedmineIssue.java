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
        String subject,
        String description,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("done_ratio") int doneRatio,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        List<RedmineAttachment> attachments,
        List<Journal> journals
) {

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
