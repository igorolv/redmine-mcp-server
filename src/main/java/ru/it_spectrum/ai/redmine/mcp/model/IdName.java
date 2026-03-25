package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdName(int id, String name) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueStatuses(@JsonProperty("issue_statuses") List<IdName> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trackers(List<IdName> trackers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuePriorities(@JsonProperty("issue_priorities") List<IdName> items) {
    }
}
