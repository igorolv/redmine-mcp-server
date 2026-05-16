package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record IssueFullContextResult(
        RedmineIssue issue,
        IssueHistoryView history,
        List<ContextIssue> contextIssues,
        List<DocumentExcerpt> documents,
        List<RedmineIssue.Journal> recentNotes,
        ContextStats stats
) {
}
