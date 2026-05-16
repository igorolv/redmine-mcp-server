package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.ContextIssue;
import ru.it_spectrum.ai.redmine.mcp.model.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.model.DocumentExcerpt;
import ru.it_spectrum.ai.redmine.mcp.model.IssueContextRole;
import ru.it_spectrum.ai.redmine.mcp.model.IssueFullContextResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ContextService {
    private static final int MAX_SIBLINGS = 20;
    private static final int MAX_CHILDREN = 20;
    private static final int MAX_RELATED = 10;
    private static final int MAX_INLINE_DOCS = 3;
    private static final int MAX_DOC_TEXT_LENGTH = 10_000;
    private static final int MAX_TOTAL_DOC_TEXT = 30_000;
    private static final int MAX_RECENT_NOTES = 10;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final RedmineClient client;
    private final AttachmentService attachmentService;
    private final IssueService issueService;

    public ContextService(RedmineClient client, AttachmentService attachmentService, IssueService issueService) {
        this.client = client;
        this.attachmentService = attachmentService;
        this.issueService = issueService;
    }

    public Optional<IssueFullContextResult> getIssueFullContext(int issueId) {
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
                addContextIssue(contextIssues, parent, new IssueContextRole(
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
                    addContextIssue(contextIssues, sibling, new IssueContextRole(
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
                    addContextIssue(contextIssues, childIssue, new IssueContextRole(
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
                    addContextIssue(contextIssues, related, new IssueContextRole(
                            "related", relType, rel.id(), issueId, relatedId, rel.delay()));
                }
            }
        }

        var documents = new ArrayList<DocumentExcerpt>();
        collectDocumentExcerpts(issue, "issue", issue.id(), documents);
        if (parent != null) {
            collectDocumentExcerpts(parent, "parent", parent.id(), documents);
        }

        List<RedmineIssue.Journal> recentNotes = List.of();
        if (issue.journals() != null) {
            var notes = issue.journals().stream()
                    .filter(j -> j.notes() != null && !j.notes().isBlank())
                    .toList();
            if (!notes.isEmpty()) {
                int startIdx = Math.max(0, notes.size() - MAX_RECENT_NOTES);
                recentNotes = notes.subList(startIdx, notes.size()).stream()
                        .map(j -> new RedmineIssue.Journal(j.id(), j.user(),
                                truncate(j.notes(), MAX_NOTE_LENGTH), j.createdOn(), j.details()))
                        .toList();
            }
        }

        var stats = new ContextStats(
                siblingsTruncated,
                childrenTruncated,
                relatedTruncated
        );

        return Optional.of(new IssueFullContextResult(
                issue,
                history,
                contextIssues.values().stream()
                        .map(ContextIssueBuilder::build)
                        .toList(),
                documents,
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

    private void collectDocumentExcerpts(RedmineIssue sourceIssue, String source,
                                         int sourceIssueId, List<DocumentExcerpt> documents) {
        if (sourceIssue.attachments() == null) return;
        int totalDocText = documents.stream().mapToInt(d -> d.text().length()).sum();
        for (var att : sourceIssue.attachments()) {
            if (documents.size() >= MAX_INLINE_DOCS || totalDocText >= MAX_TOTAL_DOC_TEXT) break;

            String ext = attachmentService.fileExtension(att);
            if (IMAGE_EXTENSIONS.contains(ext)) continue;
            if (!attachmentService.isTextExtractable(att)) continue;

            String text = attachmentService.extractText(sourceIssueId, att).orElse(null);
            if (text == null || text.isBlank()) continue;

            int allowedLength = Math.min(MAX_DOC_TEXT_LENGTH, MAX_TOTAL_DOC_TEXT - totalDocText);
            if (allowedLength <= 0) break;

            String truncatedText = truncate(text, allowedLength);
            documents.add(new DocumentExcerpt(
                    att,
                    source,
                    sourceIssueId,
                    attachmentService.detectExtractionType(att),
                    truncatedText,
                    text.length() > allowedLength
            ));
            totalDocText += truncatedText.length();
        }
    }

    private void addContextIssue(Map<Integer, ContextIssueBuilder> contextIssues,
                                 RedmineIssue issue,
                                 IssueContextRole role) {
        contextIssues.computeIfAbsent(issue.id(), ignored -> new ContextIssueBuilder(issue)).addRole(role);
    }

    private static final class ContextIssueBuilder {
        private final RedmineIssue issue;
        private final List<IssueContextRole> roles = new ArrayList<>();

        private ContextIssueBuilder(RedmineIssue issue) {
            this.issue = issue;
        }

        private void addRole(IssueContextRole role) {
            roles.add(role);
        }

        private ContextIssue build() {
            return new ContextIssue(issue, List.copyOf(roles));
        }
    }

}
