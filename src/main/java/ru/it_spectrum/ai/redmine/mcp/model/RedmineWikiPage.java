package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineWikiPage(
        String title,
        String text,
        int version,
        IdName author,
        String comments,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        List<RedmineAttachment> attachments
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(@JsonProperty("wiki_page") RedmineWikiPage wikiPage) {
    }
}
