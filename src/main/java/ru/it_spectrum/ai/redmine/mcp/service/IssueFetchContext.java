package ru.it_spectrum.ai.redmine.mcp.service;

import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IssueFetchContext {

    private final RedmineClient client;

    private final Map<Integer, RedmineIssue> issuesById = new HashMap<>();
    private final Map<String, Map<Integer, String>> versionsByProject = new HashMap<>();

    private Map<Integer, String> statuses;
    private Map<Integer, String> priorities;
    private Map<Integer, String> trackers;

    public IssueFetchContext(RedmineClient client) {
        this.client = client;
    }

    public RedmineIssue getIssue(int issueId) {
        return issuesById.computeIfAbsent(issueId, client::getIssue);
    }

    public Map<Integer, String> statuses() {
        if (statuses == null) {
            statuses = toIdNameMap(client.getIssueStatuses());
        }
        return statuses;
    }

    public Map<Integer, String> priorities() {
        if (priorities == null) {
            priorities = toIdNameMap(client.getIssuePriorities());
        }
        return priorities;
    }

    public Map<Integer, String> trackers() {
        if (trackers == null) {
            trackers = toIdNameMap(client.getTrackers());
        }
        return trackers;
    }

    public Map<Integer, String> versions(String projectId) {
        return versionsByProject.computeIfAbsent(projectId, id ->
                client.getProjectVersions(id).stream()
                        .collect(Collectors.toMap(RedmineVersion::id, RedmineVersion::name, (a, b) -> a)));
    }

    private static Map<Integer, String> toIdNameMap(List<IdName> items) {
        return items.stream().collect(Collectors.toMap(IdName::id, IdName::name, (a, b) -> a));
    }
}
