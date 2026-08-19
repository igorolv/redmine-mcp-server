package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class RedmineWikiPageMutation {

    private RedmineWikiPageMutation() {
    }

    public record Request(
            @JsonProperty("wiki_page") Fields wikiPage
    ) {
    }

    public record Fields(
            String text,
            String comments,
            @JsonProperty("parent_title") String parentTitle,
            Integer version
    ) {
    }
}
