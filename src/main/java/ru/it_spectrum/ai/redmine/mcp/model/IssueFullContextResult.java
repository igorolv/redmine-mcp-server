package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;
import java.util.Map;

public record IssueFullContextResult(
        RedmineIssue issue,
        RedmineIssue parent,
        SiblingSummary siblings,
        List<RedmineIssue.Child> children,
        Map<String, List<RedmineIssue>> relatedByType,
        List<RedmineAttachment> attachments,
        List<DocumentExcerpt> documents,
        List<RedmineIssue.Journal> recentNotes,
        int apiCalls
) {
}
