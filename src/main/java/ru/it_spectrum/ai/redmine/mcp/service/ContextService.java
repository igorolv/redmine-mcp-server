package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ContextService {
    private static final int MAX_SIBLINGS = 20;
    private static final int MAX_CHILDREN = 20;
    private static final int MAX_RELATED = 10;
    private static final int MAX_RECENT_NOTES = 10;
    private static final int MAX_NOTE_LENGTH = 500;

    private final RedmineClient client;
    private final AttachmentService attachmentService;
    private final IssueService issueService;
    private final RedmineMcpProperties properties;

    public ContextService(RedmineClient client, AttachmentService attachmentService,
                          IssueService issueService, RedmineMcpProperties properties) {
        this.client = client;
        this.attachmentService = attachmentService;
        this.issueService = issueService;
        this.properties = properties;
    }

    public ContextService(RedmineClient client, AttachmentService attachmentService,
                          IssueService issueService) {
        this(client, attachmentService, issueService, new RedmineMcpProperties(null));
    }

    public Optional<IssueFullContext> getIssueFullContext(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }
        attachmentService.snapshotIssue(issue);
        var fetchContext = new IssueFetchContext(client);
        var history = issueService.buildHistory(issue, fetchContext);

        var contextIssues = new LinkedHashMap<Integer, ContextIssueBuilder>();

        RedmineIssue parent = null;
        if (issue.parent() != null) {
            parent = client.getIssue(issue.parent().id());
            if (parent != null) {
                attachmentService.snapshotIssue(parent);
                addContextIssue(contextIssues, parent, new ContextRole(
                        "parent", null, null, issueId, parent.id(), null));
            }
        }

        boolean siblingsTruncated = false;
        if (parent != null && parent.children() != null) {
            long siblingsTotal = parent.children().stream()
                    .filter(child -> child.id() != issueId)
                    .count();
            siblingsTruncated = siblingsTotal > MAX_SIBLINGS;
            int siblingAttempts = 0;
            for (var child : parent.children()) {
                if (child.id() == issueId) continue;
                if (siblingAttempts >= MAX_SIBLINGS) break;
                siblingAttempts++;
                var sibling = client.getIssue(child.id());
                if (sibling != null) {
                    attachmentService.snapshotIssue(sibling);
                    addContextIssue(contextIssues, sibling, new ContextRole(
                            "sibling", null, null, parent.id(), sibling.id(), null));
                }
            }
        }

        boolean childrenTruncated = issue.children() != null && issue.children().size() > MAX_CHILDREN;
        if (issue.children() != null) {
            int childAttempts = 0;
            for (var child : issue.children()) {
                if (childAttempts >= MAX_CHILDREN) break;
                childAttempts++;
                var childIssue = client.getIssue(child.id());
                if (childIssue != null) {
                    attachmentService.snapshotIssue(childIssue);
                    addContextIssue(contextIssues, childIssue, new ContextRole(
                            "child", null, null, issueId, childIssue.id(), null));
                }
            }
        }

        boolean relatedTruncated = issue.relations() != null && issue.relations().size() > MAX_RELATED;
        if (issue.relations() != null && !issue.relations().isEmpty()) {
            int relCount = 0;

            for (var rel : issue.relations()) {
                if (relCount >= MAX_RELATED) break;
                int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
                String relType = formatRelationType(rel, issueId);
                var related = client.getIssue(relatedId);
                relCount++;
                if (related != null) {
                    attachmentService.snapshotIssue(related);
                    addContextIssue(contextIssues, related, new ContextRole(
                            "related", relType, rel.id(), issueId, relatedId, rel.delay()));
                }
            }
        }

        var attachments = new ArrayList<ContextAttachment>();
        int totalAttachmentText = collectContextAttachments(issue, "issue", issue.id(), attachments, 0);
        if (parent != null) {
            collectContextAttachments(parent, "parent", parent.id(), attachments, totalAttachmentText);
        }

        List<Issue.Journal> recentNotes = List.of();
        if (issue.journals() != null) {
            var notes = issue.journals().stream()
                    .filter(j -> j.notes() != null && !j.notes().isBlank())
                    .toList();
            if (!notes.isEmpty()) {
                int startIdx = Math.max(0, notes.size() - MAX_RECENT_NOTES);
                recentNotes = notes.subList(startIdx, notes.size()).stream()
                        .map(j -> new RedmineIssue.Journal(j.id(), j.user(),
                                truncate(j.notes(), MAX_NOTE_LENGTH), j.createdOn(), j.details()))
                        .map(Issue.Journal::from)
                        .toList();
            }
        }

        var stats = new ContextStats(
                siblingsTruncated,
                childrenTruncated,
                relatedTruncated
        );

        return Optional.of(new IssueFullContext(
                Issue.from(issue),
                history,
                contextIssues.values().stream()
                        .map(ContextIssueBuilder::build)
                        .toList(),
                attachments,
                recentNotes,
                stats
        ));
    }

    private String formatRelationType(RedmineIssue.Relation rel, int currentIssueId) {
        String type = rel.relationType();
        if (rel.issueId() == currentIssueId) {
            return type;
        }
        return switch (type) {
            case "blocks" -> "blocked_by";
            case "precedes" -> "follows";
            case "duplicates" -> "duplicated_by";
            case "copied_to" -> "copied_from";
            default -> type;
        };
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }

    private int collectContextAttachments(RedmineIssue sourceIssue, String source,
                                          int sourceIssueId, List<ContextAttachment> attachments,
                                          int totalAttachmentText) {
        if (sourceIssue.attachments() == null) return totalAttachmentText;

        for (var att : sourceIssue.attachments()) {
            int previewLimit = nextAttachmentPreviewLimit(totalAttachmentText);
            AttachmentContent content = attachmentService.getAttachmentContentWithinTextBudget(
                    sourceIssueId, att, previewLimit);
            attachments.add(new ContextAttachment(
                    source,
                    sourceIssueId,
                    content
            ));
            totalAttachmentText += textContentLength(content);
        }
        return totalAttachmentText;
    }

    private int nextAttachmentPreviewLimit(int totalAttachmentText) {
        var fullContext = properties.fullContext();
        int remainingTotal = Math.max(0, fullContext.totalAttachmentTextLimit() - totalAttachmentText);
        return Math.min(fullContext.attachmentTextLimit(), remainingTotal);
    }

    private int textContentLength(AttachmentContent content) {
        return content.parts().stream()
                .filter(AttachmentContent.Part::textExtracted)
                .map(AttachmentContent.Part::content)
                .filter(value -> value != null)
                .mapToInt(String::length)
                .sum();
    }

    private void addContextIssue(Map<Integer, ContextIssueBuilder> contextIssues,
                                 RedmineIssue issue,
                                 ContextRole role) {
        contextIssues.computeIfAbsent(issue.id(), ignored -> new ContextIssueBuilder(issue)).addRole(role);
    }

    private static final class ContextIssueBuilder {
        private final RedmineIssue issue;
        private final List<ContextRole> roles = new ArrayList<>();

        private ContextIssueBuilder(RedmineIssue issue) {
            this.issue = issue;
        }

        private void addRole(ContextRole role) {
            roles.add(role);
        }

        private ContextIssue build() {
            return new ContextIssue(Issue.from(issue), List.copyOf(roles));
        }
    }
}
