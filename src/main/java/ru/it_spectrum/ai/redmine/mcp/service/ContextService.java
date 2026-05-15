package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentMatch;
import ru.it_spectrum.ai.redmine.mcp.model.DocumentExcerpt;
import ru.it_spectrum.ai.redmine.mcp.model.IssueFullContextResult;
import ru.it_spectrum.ai.redmine.mcp.model.IssueNetworkResult;
import ru.it_spectrum.ai.redmine.mcp.model.LatestAttachmentResult;
import ru.it_spectrum.ai.redmine.mcp.model.NetworkEdge;
import ru.it_spectrum.ai.redmine.mcp.model.RelatedClosedIssuesResult;
import ru.it_spectrum.ai.redmine.mcp.model.SiblingSummary;
import ru.it_spectrum.ai.redmine.mcp.model.SiblingsResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContextService {
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

    public ContextService(RedmineClient client, AttachmentService attachmentService) {
        this.client = client;
        this.attachmentService = attachmentService;
    }

    public Optional<IssueFullContextResult> getIssueFullContext(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
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

        return Optional.of(new IssueFullContextResult(
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

    public SiblingsResult getIssueSiblings(int issueId) {
        var issue = client.getIssueForTree(issueId);
        if (issue == null) {
            throw new ResourceNotFoundException("issue", "#" + issueId);
        }

        if (issue.parent() == null) {
            return new SiblingsResult(issue, null, List.of(), 0, 0, 0, "no_parent");
        }

        var parent = client.getIssue(issue.parent().id());
        if (parent == null) {
            throw new ResourceNotFoundException("parent issue", "#" + issue.parent().id());
        }

        if (parent.children() == null || parent.children().size() <= 1) {
            return new SiblingsResult(issue, parent, List.of(), 0, 1, 0, "only_child");
        }

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
        return new SiblingsResult(
                currentFull != null ? currentFull : issue,
                parent,
                siblings,
                (int) closedCount,
                total,
                progressPercent,
                "ok"
        );
    }

    public Optional<RelatedClosedIssuesResult> findRelatedClosedIssues(int issueId, Integer limit) {
        int maxResults = limit != null ? Math.min(Math.max(limit, 1), 30) : 15;

        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }

        var foundIds = new java.util.LinkedHashSet<Integer>();
        foundIds.add(issueId);

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

        var similar = new ArrayList<RedmineIssue>();
        if (foundIds.size() - 1 < maxResults && issue.project() != null) {
            int remaining = maxResults - (foundIds.size() - 1);
            Integer trackerId = issue.tracker() != null ? issue.tracker().id() : null;
            Integer versionId = issue.fixedVersion() != null ? issue.fixedVersion().id() : null;

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

        return Optional.of(new RelatedClosedIssuesResult(
                issue,
                directClosed,
                closedSiblings,
                similar,
                foundIds.size() - 1
        ));
    }

    public Optional<LatestAttachmentResult> findLatestAttachment(String pattern, int issueId, Boolean searchProject) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }

        String patternLower = pattern.toLowerCase();
        var matches = new ArrayList<AttachmentMatch>();

        collectAttachmentMatches(issue, patternLower, "issue #%d".formatted(issueId), matches);

        if (issue.parent() != null) {
            var parent = client.getIssue(issue.parent().id());
            if (parent != null) {
                collectAttachmentMatches(parent, patternLower, "parent #%d".formatted(parent.id()), matches);

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

        matches.sort((a, b) -> {
            if (a.attachment().createdOn() == null) return 1;
            if (b.attachment().createdOn() == null) return -1;
            return b.attachment().createdOn().compareTo(a.attachment().createdOn());
        });

        var seen = new java.util.HashSet<Integer>();
        matches.removeIf(m -> !seen.add(m.attachment().id()));

        return Optional.of(new LatestAttachmentResult(
                pattern,
                issueId,
                matches.isEmpty() ? null : matches.getFirst(),
                matches
        ));
    }

    public IssueNetworkResult getIssueNetwork(int issueId, Integer depth) {
        int maxDepth = depth != null ? Math.min(Math.max(depth, 1), 3) : 2;
        int maxIssues = 40;

        var visited = new java.util.LinkedHashMap<Integer, RedmineIssue>();
        var edges = new ArrayList<NetworkEdge>();

        var root = client.getIssueForTree(issueId);
        if (root == null) {
            throw new ResourceNotFoundException("issue", "#" + issueId);
        }
        visited.put(issueId, root);

        var queue = new ArrayList<int[]>();
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

                var edge = new NetworkEdge(currentId, otherId, relType, rel.delay());
                if (edges.stream().noneMatch(e ->
                        (e.fromId() == edge.fromId() && e.toId() == edge.toId())
                                || (e.fromId() == edge.toId() && e.toId() == edge.fromId()
                                && e.type().equals(reverseRelType(edge.type()))))) {
                    edges.add(edge);
                }

                if (!visited.containsKey(otherId) && currentDepth < maxDepth && visited.size() < maxIssues) {
                    var other = client.getIssueForTree(otherId);
                    if (other != null) {
                        visited.put(otherId, other);
                        queue.add(new int[]{otherId, currentDepth + 1});
                    }
                }
            }

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
                .collect(Collectors.groupingBy(NetworkEdge::type, LinkedHashMap::new, Collectors.toList()));

        return new IssueNetworkResult(
                root,
                maxDepth,
                visited.size() >= maxIssues,
                visited,
                edges,
                edgesByType
        );
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
