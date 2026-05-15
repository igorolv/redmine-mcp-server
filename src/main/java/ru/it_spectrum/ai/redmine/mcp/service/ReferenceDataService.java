package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineQuery;

import java.util.List;

@Service
public class ReferenceDataService {
    private final RedmineClient client;

    public ReferenceDataService(RedmineClient client) {
        this.client = client;
    }

    public List<IdName> listStatuses() {
        return client.getIssueStatuses();
    }

    public List<IdName> listTrackers() {
        return client.getTrackers();
    }

    public List<IdName> listPriorities() {
        return client.getIssuePriorities();
    }

    public List<IdName> listIssueCategories(String projectId) {
        return client.getIssueCategories(projectId);
    }

    public List<IdName> listTimeEntryActivities() {
        return client.getTimeEntryActivities();
    }

    public RedmineQuery.Page listQueries(int offset, int limit) {
        return client.getQueries(offset, limit);
    }
}
