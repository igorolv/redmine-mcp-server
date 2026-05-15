package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.model.MyTimeEntriesResult;

import java.util.Optional;

@Service
public class TimeEntryService {
    private final RedmineClient client;

    public TimeEntryService(RedmineClient client) {
        this.client = client;
    }

    public RedmineTimeEntry.Page list(String projectId, Integer issueId, Integer userId,
                                      String from, String to, int offset, int limit) {
        return client.getTimeEntries(projectId, issueId, userId, from, to, offset, limit);
    }

    public Optional<MyTimeEntriesResult> getMyTimeEntries(String projectId, Integer issueId,
                                                          String from, String to,
                                                          int offset, int limit) {
        var user = client.getCurrentUser();
        if (user == null) {
            return Optional.empty();
        }
        var page = client.getTimeEntries(projectId, issueId, user.id(), from, to, offset, limit);
        return Optional.of(new MyTimeEntriesResult(user, page));
    }
}
