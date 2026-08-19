package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class RedmineIssueMutation {

    private RedmineIssueMutation() {
    }

    public record Fields(
            @JsonProperty("project_id") String projectId,
            @JsonProperty("tracker_id") Integer trackerId,
            @JsonProperty("status_id") Integer statusId,
            @JsonProperty("priority_id") Integer priorityId,
            String subject,
            String description,
            @JsonProperty("category_id") Integer categoryId,
            @JsonProperty("fixed_version_id") Integer fixedVersionId,
            @JsonProperty("assigned_to_id") Integer assignedToId,
            @JsonProperty("parent_issue_id") Integer parentIssueId,
            @JsonProperty("start_date") String startDate,
            @JsonProperty("due_date") String dueDate,
            @JsonProperty("done_ratio") Integer doneRatio,
            @JsonProperty("estimated_hours") Double estimatedHours,
            @JsonProperty("is_private") Boolean isPrivate,
            @JsonProperty("custom_fields") List<RedmineCustomFieldValue> customFields,
            String notes,
            List<UploadReference> uploads
    ) {
        public boolean hasChanges() {
            return projectId != null || trackerId != null || statusId != null || priorityId != null
                    || subject != null || description != null || categoryId != null || fixedVersionId != null
                    || assignedToId != null || parentIssueId != null || startDate != null || dueDate != null
                    || doneRatio != null || estimatedHours != null || isPrivate != null
                    || customFields != null || notes != null || uploads != null;
        }
    }

    public record Request(Fields issue) {
    }

    public record UploadReference(
            String token,
            String filename,
            @JsonProperty("content_type") String contentType,
            String description
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadToken(String token) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadResponse(UploadToken upload) {
    }
}
