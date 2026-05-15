package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContextTools {
    private static final int MAX_SIBLINGS = 20;
    private static final int MAX_RELATED = 10;
    private static final int MAX_INLINE_DOCS = 3;
    private static final int MAX_DOC_TEXT_LENGTH = 10_000;
    private static final int MAX_TOTAL_DOC_TEXT = 30_000;
    private static final int MAX_PARENT_DESC_LENGTH = 3_000;
    private static final int MAX_RECENT_NOTES = 10;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final RedmineClient client;
    private final AttachmentService attachmentService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public ContextTools(RedmineClient client, AttachmentService attachmentService, JsonResponses json, ToolErrors errors) {
        this.client = client;
        this.attachmentService = attachmentService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get full context needed to understand and implement a Redmine issue. " +
            "One call replaces 10+ separate tool calls. Returns: the issue with description, " +
            "parent issue context (epic/story), sibling issues (same parent — shows feature scope and progress), " +
            "related issues with descriptions, document attachments extracted inline (PDF/DOCX/XLSX), " +
            "and recent discussion notes. Ideal first call when investigating or implementing a task.")
    public String getIssueFullContext(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return errors.notFound("issue", "#" + issueId);
        }

        int fetchCount = 1;
        RedmineIssue parent = null;
        if (issue.parent() != null) {
            parent = client.getIssue(issue.parent().id());
            fetchCount++;
        }

        var siblings = new ArrayList<RedmineIssue>();
        if (parent != null && parent.children() != null && parent.children().size() > 1) {
            for (var child : parent.children()) {
                if (child.id() != issueId && siblings.size() < MAX_SIBLINGS) {
                    var sibling = client.getIssueForTree(child.id());
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
                var related = client.getIssueForTree(relatedId);
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

        return json.write(new IssueFullContextResult(
                issue,
                parent != null ? withTruncatedDescription(parent, MAX_PARENT_DESC_LENGTH) : null,
                new SiblingSummary(siblingTotal, siblingClosed, siblings),
                issue.children() != null ? issue.children() : List.of(),
                relatedByType,
                issue.attachments() != null ? issue.attachments() : List.of(),
                documents,
                recentNotes,
                fetchCount
        ));
    }

    // ── getIssueSiblings ─────────────────────────────────────────────

    @McpTool(description = "Get all sibling issues — tasks with the same parent (epic/story). " +
            "Shows the full scope of the parent feature: which parts are done, in progress, or pending. " +
            "Includes each sibling's status, assignee, progress, due date, and description snippet. " +
            "Useful for understanding context before implementing a task.")
    public String getIssueSiblings(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var issue = client.getIssueForTree(issueId);
        if (issue == null) {
            return errors.notFound("issue", "#" + issueId);
        }

        if (issue.parent() == null) {
            return json.write(new SiblingsResult(issue, null, List.of(), 0, 0, 0, "no_parent"));
        }

        var parent = client.getIssue(issue.parent().id());
        if (parent == null) {
            return errors.notFound("parent issue", "#" + issue.parent().id());
        }

        if (parent.children() == null || parent.children().size() <= 1) {
            return json.write(new SiblingsResult(issue, parent, List.of(), 0, 1, 0, "only_child"));
        }

        // Fetch all siblings with details
        var siblings = new ArrayList<RedmineIssue>();
        RedmineIssue currentFull = null;
        for (var child : parent.children()) {
            if (siblings.size() >= MAX_SIBLINGS) break;
            var sibling = client.getIssueForTree(child.id());
            if (sibling != null) {
                siblings.add(sibling);
                if (child.id() == issueId) currentFull = sibling;
            }
        }

        long closedCount = siblings.stream().filter(s -> isClosedStatus(s.status())).count();
        int total = siblings.size();

        int progressPercent = total > 0 ? (int) Math.round(100.0 * closedCount / total) : 0;
        return json.write(new SiblingsResult(
                currentFull != null ? currentFull : issue,
                parent,
                siblings,
                (int) closedCount,
                total,
                progressPercent,
                "ok"
        ));
    }

    // ── findRelatedClosedIssues ───────────────────────────────────────

    @McpTool(description = "Find closed/resolved issues related to a given issue. " +
            "Searches: direct relations, closed siblings (same parent), and similar closed issues " +
            "in the same project+version+tracker. " +
            "Useful for finding reference implementations and prior solutions before starting work.")
    public String findRelatedClosedIssues(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Maximum results, default 15", required = false) Integer limit
    ) {
        int maxResults = limit != null ? Math.min(Math.max(limit, 1), 30) : 15;

        var issue = client.getIssue(issueId);
        if (issue == null) {
            return errors.notFound("issue", "#" + issueId);
        }

        var foundIds = new java.util.LinkedHashSet<Integer>();
        foundIds.add(issueId); // exclude self

        // 1. Direct relations that are closed
        var directClosed = new ArrayList<RedmineIssue>();
        if (issue.relations() != null) {
            for (var rel : issue.relations()) {
                if (foundIds.size() - 1 >= maxResults) break;
                int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
                if (foundIds.contains(relatedId)) continue;
                var related = client.getIssueForTree(relatedId);
                if (related != null && isClosedStatus(related.status())) {
                    directClosed.add(related);
                    foundIds.add(relatedId);
                }
            }
        }

        // 2. Closed siblings (same parent)
        var closedSiblings = new ArrayList<RedmineIssue>();
        if (issue.parent() != null && foundIds.size() - 1 < maxResults) {
            var parent = client.getIssueForTree(issue.parent().id());
            if (parent != null && parent.children() != null) {
                for (var child : parent.children()) {
                    if (foundIds.size() - 1 >= maxResults) break;
                    if (foundIds.contains(child.id())) continue;
                    var sibling = client.getIssueForTree(child.id());
                    if (sibling != null && isClosedStatus(sibling.status())) {
                        closedSiblings.add(sibling);
                        foundIds.add(child.id());
                    }
                }
            }
        }

        // 3. Similar closed issues (same project + tracker, optionally same version)
        var similar = new ArrayList<RedmineIssue>();
        if (foundIds.size() - 1 < maxResults && issue.project() != null) {
            int remaining = maxResults - (foundIds.size() - 1);
            Integer trackerId = issue.tracker() != null ? issue.tracker().id() : null;
            Integer versionId = issue.fixedVersion() != null ? issue.fixedVersion().id() : null;

            // Try same version first
            if (versionId != null) {
                var page = client.listIssues(
                        String.valueOf(issue.project().id()), "closed", trackerId,
                        null, null, versionId, "updated_on:desc", null, 0, remaining + foundIds.size());
                for (var candidate : page.issues()) {
                    if (foundIds.contains(candidate.id())) continue;
                    similar.add(candidate);
                    foundIds.add(candidate.id());
                    if (similar.size() >= remaining) break;
                }
            }

            // If not enough, try same project+tracker without version filter
            if (similar.size() < remaining) {
                int stillNeeded = remaining - similar.size();
                var page = client.listIssues(
                        String.valueOf(issue.project().id()), "closed", trackerId,
                        null, null, null, "updated_on:desc", null, 0, stillNeeded + foundIds.size());
                for (var candidate : page.issues()) {
                    if (foundIds.contains(candidate.id())) continue;
                    similar.add(candidate);
                    foundIds.add(candidate.id());
                    if (similar.size() >= remaining) break;
                }
            }
        }

        return json.write(new RelatedClosedIssuesResult(
                issue,
                directClosed,
                closedSiblings,
                similar,
                foundIds.size() - 1
        ));
    }

    // ── findLatestAttachment ──────────────────────────────────────────

    @McpTool(description = "Find the latest version of a document/attachment by filename pattern. " +
            "Searches across the issue itself, its parent, siblings, and related issues. " +
            "Returns all matching attachments sorted by date (newest first). " +
            "Useful for finding the latest spec, requirements, or design document.")
    public String findLatestAttachment(
            @McpToolParam(description = "Filename pattern to search (case-insensitive substring match, e.g. 'spec', 'ТЗ', 'requirements')") String pattern,
            @McpToolParam(description = "Issue ID to start search from") int issueId,
            @McpToolParam(description = "Also search in project-wide recent issues (true/false, default false)", required = false) Boolean searchProject
    ) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return errors.notFound("issue", "#" + issueId);
        }

        String patternLower = pattern.toLowerCase();
        var matches = new ArrayList<AttachmentMatch>();

        // Search in the issue itself
        collectAttachmentMatches(issue, patternLower, "issue #%d".formatted(issueId), matches);

        // Search in parent
        if (issue.parent() != null) {
            var parent = client.getIssue(issue.parent().id());
            if (parent != null) {
                collectAttachmentMatches(parent, patternLower, "parent #%d".formatted(parent.id()), matches);

                // Search in siblings
                if (parent.children() != null) {
                    for (var child : parent.children()) {
                        if (child.id() == issueId) continue;
                        if (matches.size() >= 30) break;
                        var sibling = client.getIssue(child.id());
                        if (sibling != null) {
                            collectAttachmentMatches(sibling, patternLower,
                                    "sibling #%d".formatted(child.id()), matches);
                        }
                    }
                }
            }
        }

        // Search in related issues
        if (issue.relations() != null) {
            for (var rel : issue.relations()) {
                if (matches.size() >= 30) break;
                int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
                var related = client.getIssue(relatedId);
                if (related != null) {
                    collectAttachmentMatches(related, patternLower,
                            "related #%d".formatted(relatedId), matches);
                }
            }
        }

        // Optionally search project-wide
        if (Boolean.TRUE.equals(searchProject) && issue.project() != null) {
            var page = client.listIssues(String.valueOf(issue.project().id()), "*",
                    null, null, null, null, "updated_on:desc", null, 0, 20);
            for (var candidate : page.issues()) {
                if (matches.size() >= 30) break;
                var full = client.getIssue(candidate.id());
                if (full != null) {
                    collectAttachmentMatches(full, patternLower,
                            "project issue #%d".formatted(candidate.id()), matches);
                }
            }
        }

        // Sort by date descending (newest first)
        matches.sort((a, b) -> {
            if (a.attachment.createdOn() == null) return 1;
            if (b.attachment.createdOn() == null) return -1;
            return b.attachment.createdOn().compareTo(a.attachment.createdOn());
        });

        // Deduplicate by attachment ID
        var seen = new java.util.HashSet<Integer>();
        matches.removeIf(m -> !seen.add(m.attachment.id()));

        return json.write(new LatestAttachmentResult(
                pattern,
                issueId,
                matches.isEmpty() ? null : matches.getFirst(),
                matches
        ));
    }

    // ── getIssueNetwork ───────────────────────────────────────────────

    @McpTool(description = "Build a full network of all relation types for an issue. " +
            "Unlike getIssueTree (parent/child only), this traverses ALL relation types: " +
            "relates, blocks/blocked_by, precedes/follows, duplicates, copied_to. " +
            "Shows each related issue with status, assignee, and due date. " +
            "Follows relations up to the specified depth.")
    public String getIssueNetwork(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How many levels of relations to follow, default 2, max 3", required = false) Integer depth
    ) {
        int maxDepth = depth != null ? Math.min(Math.max(depth, 1), 3) : 2;
        int maxIssues = 40;

        var visited = new java.util.LinkedHashMap<Integer, RedmineIssue>();
        var edges = new ArrayList<NetworkEdge>();

        // Fetch root
        var root = client.getIssueForTree(issueId);
        if (root == null) {
            return errors.notFound("issue", "#" + issueId);
        }
        visited.put(issueId, root);

        // BFS traversal
        var queue = new ArrayList<int[]>(); // [issueId, currentDepth]
        queue.add(new int[]{issueId, 0});

        int qi = 0;
        while (qi < queue.size() && visited.size() < maxIssues) {
            int[] item = queue.get(qi++);
            int currentId = item[0];
            int currentDepth = item[1];

            var current = visited.get(currentId);
            if (current == null || current.relations() == null) continue;

            for (var rel : current.relations()) {
                int otherId = rel.issueId() == currentId ? rel.issueToId() : rel.issueId();
                String relType = formatRelationType(rel, currentId);

                // Add edge (avoid duplicate edges)
                var edge = new NetworkEdge(currentId, otherId, relType, rel.delay());
                if (edges.stream().noneMatch(e ->
                        (e.fromId == edge.fromId && e.toId == edge.toId)
                                || (e.fromId == edge.toId && e.toId == edge.fromId && e.type.equals(reverseRelType(edge.type))))) {
                    edges.add(edge);
                }

                // Fetch neighbor if not visited and within depth
                if (!visited.containsKey(otherId) && currentDepth < maxDepth && visited.size() < maxIssues) {
                    var other = client.getIssueForTree(otherId);
                    if (other != null) {
                        visited.put(otherId, other);
                        queue.add(new int[]{otherId, currentDepth + 1});
                    }
                }
            }

            // Also traverse parent/child as implicit relations
            if (current.parent() != null && !visited.containsKey(current.parent().id())
                    && currentDepth < maxDepth && visited.size() < maxIssues) {
                var parentIssue = client.getIssueForTree(current.parent().id());
                if (parentIssue != null) {
                    visited.put(parentIssue.id(), parentIssue);
                    edges.add(new NetworkEdge(current.parent().id(), currentId, "parent_of", null));
                    queue.add(new int[]{parentIssue.id(), currentDepth + 1});
                }
            }
            if (current.children() != null) {
                for (var child : current.children()) {
                    if (!visited.containsKey(child.id()) && currentDepth < maxDepth && visited.size() < maxIssues) {
                        var childIssue = client.getIssueForTree(child.id());
                        if (childIssue != null) {
                            visited.put(childIssue.id(), childIssue);
                            edges.add(new NetworkEdge(currentId, child.id(), "parent_of", null));
                            queue.add(new int[]{child.id(), currentDepth + 1});
                        }
                    }
                }
            }
        }

        var edgesByType = edges.stream()
                .collect(Collectors.groupingBy(e -> e.type, LinkedHashMap::new, Collectors.toList()));

        return json.write(new IssueNetworkResult(
                root,
                maxDepth,
                visited.size() >= maxIssues,
                visited,
                edges,
                edgesByType
        ));
    }

    // --- Result models ---

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

    public record SiblingSummary(int total, int closed, List<RedmineIssue> issues) {
    }

    public record DocumentExcerpt(
            RedmineAttachment attachment,
            String source,
            int sourceIssueId,
            String extractionType,
            String text,
            boolean truncated
    ) {
    }

    public record SiblingsResult(
            RedmineIssue issue,
            RedmineIssue parent,
            List<RedmineIssue> siblings,
            int closed,
            int total,
            int progressPercent,
            String status
    ) {
    }

    public record RelatedClosedIssuesResult(
            RedmineIssue issue,
            List<RedmineIssue> direct,
            List<RedmineIssue> siblings,
            List<RedmineIssue> similar,
            int total
    ) {
    }

    public record LatestAttachmentResult(
            String pattern,
            int issueId,
            AttachmentMatch latest,
            List<AttachmentMatch> matches
    ) {
    }

    public record IssueNetworkResult(
            RedmineIssue root,
            int depth,
            boolean limitReached,
            Map<Integer, RedmineIssue> nodes,
            List<NetworkEdge> edges,
            Map<String, List<NetworkEdge>> edgesByType
    ) {
    }

    // --- Helpers for findLatestAttachment ---

    public record AttachmentMatch(RedmineAttachment attachment, String source) {
    }

    private void collectAttachmentMatches(RedmineIssue issue, String patternLower,
                                           String source, List<AttachmentMatch> matches) {
        if (issue.attachments() == null) return;
        for (var att : issue.attachments()) {
            if (att.filename() != null && att.filename().toLowerCase().contains(patternLower)) {
                matches.add(new AttachmentMatch(att, source));
            }
        }
    }

    // --- Helpers for getIssueNetwork ---

    public record NetworkEdge(int fromId, int toId, String type, Integer delay) {
    }

    private String reverseRelType(String type) {
        return switch (type) {
            case "blocks" -> "blocked_by";
            case "blocked_by" -> "blocks";
            case "precedes" -> "follows";
            case "follows" -> "precedes";
            case "duplicates" -> "duplicated_by";
            case "duplicated_by" -> "duplicates";
            case "copied_to" -> "copied_from";
            case "copied_from" -> "copied_to";
            case "parent_of" -> "child_of";
            case "child_of" -> "parent_of";
            default -> type;
        };
    }

    // --- Relation helpers ---

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

    // --- Status helpers ---

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

            String text = attachmentService.extractText(att).orElse(null);
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

    private RedmineIssue withTruncatedDescription(RedmineIssue issue, int maxLength) {
        String description = issue.description();
        if (description != null && description.length() > maxLength) {
            description = truncate(description, maxLength);
        }
        return new RedmineIssue(
                issue.id(), issue.project(), issue.tracker(), issue.status(), issue.priority(),
                issue.author(), issue.assignedTo(), issue.parent(), issue.fixedVersion(), issue.category(),
                issue.subject(), description, issue.startDate(), issue.dueDate(), issue.doneRatio(),
                issue.estimatedHours(), issue.spentHours(), issue.isPrivate(),
                issue.createdOn(), issue.updatedOn(), issue.customFields(), issue.attachments(),
                issue.journals(), issue.relations(), issue.children()
        );
    }
}
