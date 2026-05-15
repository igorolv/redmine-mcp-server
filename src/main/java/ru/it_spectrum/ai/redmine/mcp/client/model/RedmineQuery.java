package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineQuery(
        int id,
        String name,
        @JsonProperty("is_public") boolean isPublic,
        @JsonProperty("project_id") Integer projectId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(List<RedmineQuery> queries,
                       @JsonProperty("total_count") int totalCount,
                       int offset,
                       int limit) {
    }
}
