package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.QueryPage;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;

import java.util.List;

@Service
public class ReferenceDataService {
    private final RedmineClient client;

    public ReferenceDataService(RedmineClient client) {
        this.client = client;
    }

    public List<Ref> listStatuses() {
        return toRefs(client.getIssueStatuses());
    }

    public List<Ref> listTrackers() {
        return toRefs(client.getTrackers());
    }

    public List<Ref> listPriorities() {
        return toRefs(client.getIssuePriorities());
    }

    public List<Ref> listIssueCategories(String projectId) {
        return toRefs(client.getIssueCategories(projectId));
    }

    public List<Ref> listTimeEntryActivities() {
        return toRefs(client.getTimeEntryActivities());
    }

    public QueryPage listQueries(int offset, int limit) {
        return QueryPage.from(client.getQueries(offset, limit));
    }

    private static List<Ref> toRefs(List<IdName> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().map(Ref::from).toList();
    }
}
