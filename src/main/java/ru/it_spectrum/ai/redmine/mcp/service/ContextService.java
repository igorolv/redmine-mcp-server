package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextIssue;
import ru.it_spectrum.ai.redmine.mcp.api.ContextRole;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ContextService {
    private final RedmineClient client;
    private final AttachmentService attachmentService;
    private final IssueService issueService;
    private final RelatedRefBuilder relatedRefBuilder;
    private final RedmineMcpProperties properties;

    public ContextService(RedmineClient client, AttachmentService attachmentService,
                          IssueService issueService, RelatedRefBuilder relatedRefBuilder,
                          RedmineMcpProperties properties) {
        this.client = client;
        this.attachmentService = attachmentService;
        this.issueService = issueService;
        this.relatedRefBuilder = relatedRefBuilder;
        this.properties = properties;
    }

    public Optional<IssueFullContext> getIssueFullContext(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }
        attachmentService.snapshotIssue(issue);
        var fetchContext = new IssueFetchContext(client);
        var history = issueService.buildHistory(issue, fetchContext);

        var fetchResult = relatedRefBuilder.fetchRelated(issue);

        var contextIssues = fetchResult.entries().stream()
                .map(entry -> new ContextIssue(Issue.from(entry.issue()), entry.roles()))
                .toList();

        RedmineIssue parent = fetchResult.entries().stream()
                .filter(entry -> entry.roles().stream()
                        .anyMatch(role -> role.role() == ContextRole.Kind.PARENT))
                .map(RelatedRefBuilder.Fetched::issue)
                .findFirst()
                .orElse(null);

        var attachments = new ArrayList<ContextAttachment>();
        collectContextAttachments(issue, "issue", issue.id(), attachments);
        if (parent != null) {
            collectContextAttachments(parent, "parent", parent.id(), attachments);
        }

        List<Issue.Journal> recentNotes = List.of();
        if (issue.journals() != null) {
            var notes = issue.journals().stream()
                    .filter(j -> j.notes() != null && !j.notes().isBlank())
                    .toList();
            if (!notes.isEmpty()) {
                int startIdx = Math.max(0, notes.size() - properties.fullContext().maxRecentNotes());
                recentNotes = notes.subList(startIdx, notes.size()).stream()
                        .map(Issue.Journal::from)
                        .toList();
            }
        }

        var stats = new ContextStats(
                fetchResult.siblingsTruncated(),
                fetchResult.childrenTruncated(),
                fetchResult.relatedTruncated()
        );

        return Optional.of(new IssueFullContext(
                Issue.from(issue),
                history,
                contextIssues,
                attachments,
                recentNotes,
                stats,
                null
        ));
    }

    private void collectContextAttachments(RedmineIssue sourceIssue, String source,
                                           int sourceIssueId, List<ContextAttachment> attachments) {
        if (sourceIssue.attachments() == null) return;

        int partLimit = properties.attachment().perPartChars();
        for (var att : sourceIssue.attachments()) {
            var content = attachmentService.getAttachmentContent(sourceIssueId, att, partLimit);
            attachments.add(new ContextAttachment(source, sourceIssueId, content));
        }
    }
}
