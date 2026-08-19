package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class RedmineTimeEntryMutation {

    private RedmineTimeEntryMutation() {
    }

    public record Fields(
            @JsonProperty("project_id") Integer projectId,
            @JsonProperty("issue_id") Integer issueId,
            Double hours,
            @JsonProperty("activity_id") Integer activityId,
            @JsonProperty("spent_on") String spentOn,
            String comments,
            @JsonProperty("custom_fields") List<RedmineCustomFieldValue> customFields
    ) {
    }

    public record Request(
            @JsonProperty("time_entry") Fields timeEntry
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateResponse(
            @JsonProperty("time_entry") RedmineTimeEntry timeEntry
    ) {
    }
}
