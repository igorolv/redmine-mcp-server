package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineProject(
        int id,
        String name,
        String identifier,
        String description,
        String homepage,
        IdName parent,
        int status,
        @JsonProperty("is_public") boolean isPublic,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        List<IdName> trackers,
        @JsonProperty("enabled_modules") List<NameOnly> enabledModules
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NameOnly(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(RedmineProject project) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            List<RedmineProject> projects,
            @JsonProperty("total_count") int totalCount,
            int offset,
            int limit
    ) {
    }
}
