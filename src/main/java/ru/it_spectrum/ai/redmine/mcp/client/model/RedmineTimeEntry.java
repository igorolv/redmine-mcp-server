package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineTimeEntry(
        int id,
        IdName project,
        IdName issue,
        IdName user,
        IdName activity,
        double hours,
        String comments,
        @JsonProperty("spent_on") String spentOn,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            @JsonProperty("time_entries") List<RedmineTimeEntry> timeEntries,
            @JsonProperty("total_count") int totalCount,
            int offset,
            int limit
    ) {
    }
}
