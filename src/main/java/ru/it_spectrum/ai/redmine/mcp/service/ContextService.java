package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.DocumentExcerpt;
import ru.it_spectrum.ai.redmine.mcp.model.IssueFullContextResult;
import ru.it_spectrum.ai.redmine.mcp.model.SiblingSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ContextService {
    private static final int MAX_SIBLINGS = 20;
    private static final int MAX_RELATED = 10;
    private static final int MAX_INLINE_DOCS = 3;
    private static final int MAX_DOC_TEXT_LENGTH = 10_000;
    private static final int MAX_TOTAL_DOC_TEXT = 30_000;
    private static final int MAX_RECENT_NOTES = 10;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final RedmineClient client;
    private final AttachmentService attachmentService;

    public ContextService(RedmineClient client, AttachmentService attachmentService) {
        this.client = client;
        this.attachmentService = attachmentService;
    }

    public Optional<IssueFullContextResult> getIssueFullContext(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }
        attachmentService.snapshotIssue(issue);

        int fetchCount = 1;
        RedmineIssue parent = null;
        if (issue.parent() != null) {
            parent = client.getIssue(issue.parent().id());
            attachmentService.snapshotIssue(parent);
            fetchCount++;
        }

        var siblings = new ArrayList<RedmineIssue>();
        if (parent != null && parent.children() != null && parent.children().size() > 1) {
            for (var child : parent.children()) {
                if (child.id() != issueId && siblings.size() < MAX_SIBLINGS) {
                    var sibling = client.getIssue(child.id());
                    fetchCount++;
                    if (sibling != null) siblings.add(sibling);
                }
            }
        }

        var relatedByType = new LinkedHashMap<String, List<RedmineIssue>>();
        if (issue.relations() != null && !issue.relations().isEmpty()) {
            int relCount = 0;

            for (var rel : issue.relations()) {
                if (relCount >= MAX_RELATED) break;
                int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
                String relType = formatRelationType(rel, issueId);
                var related = client.getIssue(relatedId);
                fetchCount++;
                relCount++;
                if (related != null) {
                    relatedByType.computeIfAbsent(relType, k -> new ArrayList<>()).add(related);
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

        int siblingTotal = siblings.size() + (parent != null ? 1 : 0);
        int siblingClosed = (int) siblings.stream().filter(s -> isClosedStatus(s.status())).count();

        return Optional.of(new IssueFullContextResult(
                issue,
                parent,
                new SiblingSummary(siblingTotal, siblingClosed, siblings),
                issue.children() != null ? issue.children() : List.of(),
                relatedByType,
                issue.attachments() != null ? issue.attachments() : List.of(),
                documents,
                recentNotes,
                fetchCount
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

    private boolean isClosedStatus(IdName status) {
        if (status == null) return false;
        String lower = status.name().toLowerCase();
        return lower.contains("closed") || lower.contains("rejected")
                || lower.contains("resolved") || lower.contains("done");
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

}
