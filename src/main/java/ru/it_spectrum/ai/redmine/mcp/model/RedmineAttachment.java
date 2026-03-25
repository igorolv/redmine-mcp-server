package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineAttachment(
        int id,
        String filename,
        long filesize,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("content_url") String contentUrl,
        String description,
        IdName author,
        @JsonProperty("created_on") String createdOn
) {

    /** Wrapper for {"attachment": {...}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(RedmineAttachment attachment) {
    }
}
