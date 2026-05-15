package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineSearchResult(
        List<ResultItem> results,
        @JsonProperty("total_count") int totalCount,
        int offset,
        int limit
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultItem(
            int id,
            String title,
            String type,
            String url,
            String description,
            String datetime
    ) {
    }
}
