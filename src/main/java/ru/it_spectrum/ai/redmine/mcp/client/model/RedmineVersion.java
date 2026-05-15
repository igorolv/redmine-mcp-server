package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineVersion(
        int id,
        IdName project,
        String name,
        String description,
        String status,
        @JsonProperty("due_date") String dueDate,
        String sharing,
        @JsonProperty("wiki_page_title") String wikiPageTitle,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(List<RedmineVersion> versions) {
    }
}
