package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import java.nio.charset.StandardCharsets;
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
    private static final int MAX_RELATED_DESC_LENGTH = 1_500;
    private static final int MAX_RECENT_NOTES = 10;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final RedmineClient client;
    private final DocumentTextExtractor textExtractor;

    public ContextTools(RedmineClient client, DocumentTextExtractor textExtractor) {
        this.client = client;
        this.textExtractor = textExtractor;
    }

    @McpTool(description = "Get full context needed to understand and implement a Redmine issue. " +
            "One call replaces 10+ separate tool calls. Returns: the issue with description, " +
            "parent issue context (epic/story), sibling issues (same parent — shows feature scope and progress), " +
            "related issues with descriptions, document attachments extracted inline (PDF/DOCX/XLSX), " +
            "and recent discussion notes. Ideal first call when investigating or implementing a task.")
    public String getIssueFullContext(
            @McpToolParam(description = "Issue ID number") int issueId,
            McpSyncRequestContext context
    ) {
        ProgressSupport.report(context, 0, 6, "Loading issue");
        // 1. Fetch the issue with full details
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var sb = new StringBuilder();
        int fetchCount = 1;

        // === Issue header ===
        sb.append("=== Issue #%d: %s ===\n".formatted(issue.id(), issue.subject()));
        sb.append("Project: %s | Tracker: %s | Status: %s | Priority: %s\n".formatted(
                name(issue.project()), name(issue.tracker()), name(issue.status()), name(issue.priority())));
        sb.append("Author: %s".formatted(name(issue.author())));
        if (issue.assignedTo() != null) sb.append(" | Assigned: %s".formatted(issue.assignedTo().name()));
        if (issue.fixedVersion() != null) sb.append(" | Version: %s".formatted(issue.fixedVersion().name()));
        sb.append("\n");
        if (issue.startDate() != null) sb.append("Start: %s".formatted(issue.startDate()));
        if (issue.dueDate() != null) sb.append(" | Due: %s".formatted(issue.dueDate()));
        if (issue.estimatedHours() != null) sb.append(" | Est: %.1fh".formatted(issue.estimatedHours()));
        if (issue.spentHours() != null && issue.spentHours() > 0) sb.append(" | Spent: %.1fh".formatted(issue.spentHours()));
        sb.append(" | Done: %d%%\n".formatted(issue.doneRatio()));

        // Custom fields
        appendCustomFields(sb, issue.customFields());

        // Description
        if (issue.description() != null && !issue.description().isBlank()) {
            sb.append("\n--- Description ---\n");
            sb.append(issue.description());
            sb.append("\n");
        }

        // === Parent context ===
        RedmineIssue parent = null;
        if (issue.parent() != null) {
            ProgressSupport.report(context, 1, 6, "Loading parent context");
            parent = client.getIssue(issue.parent().id());
            fetchCount++;
            if (parent != null) {
                sb.append("\n--- Parent: #%d %s ---\n".formatted(parent.id(), parent.subject()));
                sb.append("Status: %s | Assigned: %s | Done: %d%%\n".formatted(
                        name(parent.status()),
                        parent.assignedTo() != null ? parent.assignedTo().name() : "\u2014",
                        parent.doneRatio()));
                if (parent.description() != null && !parent.description().isBlank()) {
                    sb.append(truncate(parent.description(), MAX_PARENT_DESC_LENGTH));
                    sb.append("\n");
                }
            }
        }

        // === Siblings (same parent) ===
        if (parent != null && parent.children() != null && parent.children().size() > 1) {
            ProgressSupport.report(context, 2, 6, "Loading sibling issues");
            var siblings = new ArrayList<RedmineIssue>();
            for (var child : parent.children()) {
                if (child.id() != issueId && siblings.size() < MAX_SIBLINGS) {
                    var sibling = client.getIssueForTree(child.id());
                    fetchCount++;
                    if (sibling != null) siblings.add(sibling);
                }
            }

            if (!siblings.isEmpty()) {
                long closedCount = siblings.stream().filter(s -> isClosedStatus(s.status())).count();
                int total = siblings.size() + 1; // +1 for current issue
                sb.append("\n--- Siblings (%d issues, %d/%d closed) ---\n".formatted(
                        total, closedCount, total));
                for (var sibling : siblings) {
                    String marker = isClosedStatus(sibling.status()) ? "\u2713" : "\u25cb";
                    sb.append("  %s #%d %s [%s]".formatted(marker, sibling.id(), sibling.subject(), name(sibling.status())));
                    if (sibling.assignedTo() != null) sb.append(" (%s)".formatted(sibling.assignedTo().name()));
                    if (sibling.doneRatio() > 0) sb.append(" done:%d%%".formatted(sibling.doneRatio()));
                    if (sibling.dueDate() != null) sb.append(" due:%s".formatted(sibling.dueDate()));
                    sb.append("\n");
                }
            }
        }

        // === Children (subtasks) ===
        if (issue.children() != null && !issue.children().isEmpty()) {
            sb.append("\n--- Subtasks (%d) ---\n".formatted(issue.children().size()));
            for (var child : issue.children()) {
                sb.append("  - #%d %s".formatted(child.id(), child.subject()));
                if (child.tracker() != null) sb.append(" [%s]".formatted(child.tracker().name()));
                sb.append("\n");
            }
        }

        // === Related issues with descriptions ===
        if (issue.relations() != null && !issue.relations().isEmpty()) {
            ProgressSupport.report(context, 3, 6, "Loading related issues");
            var relatedByType = new LinkedHashMap<String, List<RedmineIssue>>();
            var relTypeMap = new LinkedHashMap<Integer, String>();
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
                    relTypeMap.put(relatedId, relType);
                }
            }

            if (!relatedByType.isEmpty()) {
                int totalRelated = relatedByType.values().stream().mapToInt(List::size).sum();
                sb.append("\n--- Related Issues (%d) ---\n".formatted(totalRelated));
                relatedByType.forEach((relType, issues) -> {
                    for (var related : issues) {
                        sb.append("  %s #%d: %s [%s]".formatted(
                                relType, related.id(), related.subject(), name(related.status())));
                        if (related.assignedTo() != null)
                            sb.append(" (%s)".formatted(related.assignedTo().name()));
                        sb.append("\n");
                        if (related.description() != null && !related.description().isBlank()) {
                            String desc = truncate(related.description(), MAX_RELATED_DESC_LENGTH);
                            // Indent description
                            sb.append("    ");
                            sb.append(desc.replace("\n", "\n    "));
                            sb.append("\n");
                        }
                    }
                });
            }
        }

        // === Attachments ===
        if (issue.attachments() != null && !issue.attachments().isEmpty()) {
            ProgressSupport.report(context, 4, 6, "Extracting issue attachments");
            sb.append("\n--- Attachments (%d) ---\n".formatted(issue.attachments().size()));
            for (var att : issue.attachments()) {
                sb.append("  [%d] %s (%s, %s)\n".formatted(
                        att.id(), att.filename(),
                        att.contentType() != null ? att.contentType() : "?",
                        formatSize(att.filesize())));
            }

            // Auto-extract text from document/text attachments
            int docsExtracted = 0;
            int totalDocText = 0;
            for (var att : issue.attachments()) {
                if (docsExtracted >= MAX_INLINE_DOCS || totalDocText >= MAX_TOTAL_DOC_TEXT) break;

                String ext = textExtractor.getFileExtension(att.filename());
                if (IMAGE_EXTENSIONS.contains(ext)) continue;

                if (!textExtractor.isTextExtractable(att.filename(), att.contentType())) continue;

                String text = textExtractor.extractText(att);
                if (text == null || text.isBlank()) continue;

                int allowedLength = Math.min(MAX_DOC_TEXT_LENGTH, MAX_TOTAL_DOC_TEXT - totalDocText);
                if (allowedLength <= 0) break;

                sb.append("\n--- %s (extracted) ---\n".formatted(att.filename()));
                sb.append(truncate(text, allowedLength));
                sb.append("\n");
                docsExtracted++;
                totalDocText += Math.min(text.length(), allowedLength);
            }

            // Also extract from parent's attachments (specs are often there)
            if (parent != null && parent.attachments() != null) {
                ProgressSupport.report(context, 5, 6, "Extracting parent attachments");
                for (var att : parent.attachments()) {
                    if (docsExtracted >= MAX_INLINE_DOCS || totalDocText >= MAX_TOTAL_DOC_TEXT) break;

                    String ext = textExtractor.getFileExtension(att.filename());
                    if (IMAGE_EXTENSIONS.contains(ext)) continue;
                    if (!textExtractor.isTextExtractable(att.filename(), att.contentType())) continue;

                    String text = textExtractor.extractText(att);
                    if (text == null || text.isBlank()) continue;

                    int allowedLength = Math.min(MAX_DOC_TEXT_LENGTH, MAX_TOTAL_DOC_TEXT - totalDocText);
                    if (allowedLength <= 0) break;

                    sb.append("\n--- %s (from parent #%d) ---\n".formatted(att.filename(), parent.id()));
                    sb.append(truncate(text, allowedLength));
                    sb.append("\n");
                    docsExtracted++;
                    totalDocText += Math.min(text.length(), allowedLength);
                }
            }
        }

        // === Recent notes ===
        ProgressSupport.report(context, 6, 6, "Formatting recent notes");
        if (issue.journals() != null) {
            var notes = issue.journals().stream()
                    .filter(j -> j.notes() != null && !j.notes().isBlank())
                    .toList();
            if (!notes.isEmpty()) {
                int startIdx = Math.max(0, notes.size() - MAX_RECENT_NOTES);
                var recentNotes = notes.subList(startIdx, notes.size());
                sb.append("\n--- Recent Notes (%d of %d) ---\n".formatted(recentNotes.size(), notes.size()));
                for (var journal : recentNotes) {
                    sb.append("\n  [%s] %s:\n".formatted(
                            formatTimestamp(journal.createdOn()),
                            journal.user() != null ? journal.user().name() : "unknown"));
                    String note = truncate(journal.notes(), MAX_NOTE_LENGTH);
                    sb.append("  ");
                    sb.append(note.replace("\n", "\n  "));
                    sb.append("\n");
                }
            }
        }

        sb.append("\n[%d API calls made]\n".formatted(fetchCount));
        ProgressSupport.done(context, "Issue context loaded");

        return sb.toString();
    }

    public String getIssueFullContext(int issueId) {
        return getIssueFullContext(issueId, null);
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
            return "Issue #%d not found".formatted(issueId);
        }

        if (issue.parent() == null) {
            return "Issue #%d has no parent — cannot determine siblings".formatted(issueId);
        }

        var parent = client.getIssue(issue.parent().id());
        if (parent == null) {
            return "Parent issue #%d not found".formatted(issue.parent().id());
        }

        if (parent.children() == null || parent.children().size() <= 1) {
            return "Issue #%d is the only child of #%d %s".formatted(issueId, parent.id(), parent.subject());
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

        var sb = new StringBuilder();
        sb.append("Siblings of #%d: %s\n".formatted(issueId, issue.subject()));
        sb.append("Parent: #%d %s [%s]\n".formatted(parent.id(), parent.subject(), name(parent.status())));
        sb.append("Progress: %d/%d closed (%d%%)\n\n".formatted(
                closedCount, total, total > 0 ? Math.round(100.0 * closedCount / total) : 0));

        for (var sibling : siblings) {
            boolean isCurrent = sibling.id() == issueId;
            String marker;
            if (isCurrent) marker = "\u25b6";
            else if (isClosedStatus(sibling.status())) marker = "\u2713";
            else marker = "\u25cb";

            sb.append("%s #%d %s\n".formatted(marker, sibling.id(), sibling.subject()));
            sb.append("  [%s] %s | %s".formatted(
                    name(sibling.tracker()), name(sibling.status()), name(sibling.priority())));
            if (sibling.assignedTo() != null) sb.append(" | %s".formatted(sibling.assignedTo().name()));
            sb.append("\n");
            if (sibling.doneRatio() > 0) sb.append("  Done: %d%%".formatted(sibling.doneRatio()));
            if (sibling.dueDate() != null) sb.append("  Due: %s".formatted(sibling.dueDate()));
            if (sibling.estimatedHours() != null) sb.append("  Est: %.1fh".formatted(sibling.estimatedHours()));
            if (sibling.doneRatio() > 0 || sibling.dueDate() != null || sibling.estimatedHours() != null) {
                sb.append("\n");
            }

            // Short description for closed siblings (reference implementations)
            if (!isCurrent && sibling.description() != null && !sibling.description().isBlank()) {
                String desc = sibling.description().replace("\n", " ").strip();
                if (desc.length() > 150) desc = desc.substring(0, 150) + "...";
                sb.append("  %s\n".formatted(desc));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ── findRelatedClosedIssues ───────────────────────────────────────

    @McpTool(description = "Find closed/resolved issues related to a given issue. " +
            "Searches: direct relations, closed siblings (same parent), and similar closed issues " +
            "in the same project+version+tracker. " +
            "Useful for finding reference implementations and prior solutions before starting work.")
    public String findRelatedClosedIssues(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Maximum results, default 15", required = false) Integer limit,
            McpSyncRequestContext context
    ) {
        int maxResults = limit != null ? Math.min(Math.max(limit, 1), 30) : 15;
        ProgressSupport.report(context, 0, 3, "Loading issue and direct relations");

        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var sb = new StringBuilder();
        sb.append("Related closed issues for #%d: %s\n\n".formatted(issue.id(), issue.subject()));

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

        if (!directClosed.isEmpty()) {
            sb.append("Direct relations (closed):\n");
            for (var related : directClosed) {
                String relType = issue.relations().stream()
                        .filter(r -> (r.issueId() == related.id() || r.issueToId() == related.id()))
                        .findFirst()
                        .map(r -> formatRelationType(r, issueId))
                        .orElse("relates");
                appendClosedIssue(sb, related, relType);
            }
            sb.append("\n");
        }

        // 2. Closed siblings (same parent)
        if (issue.parent() != null && foundIds.size() - 1 < maxResults) {
            ProgressSupport.report(context, 1, 3, "Scanning sibling issues");
            var parent = client.getIssueForTree(issue.parent().id());
            if (parent != null && parent.children() != null) {
                var closedSiblings = new ArrayList<RedmineIssue>();
                for (var child : parent.children()) {
                    if (foundIds.size() - 1 >= maxResults) break;
                    if (foundIds.contains(child.id())) continue;
                    var sibling = client.getIssueForTree(child.id());
                    if (sibling != null && isClosedStatus(sibling.status())) {
                        closedSiblings.add(sibling);
                        foundIds.add(child.id());
                    }
                }
                if (!closedSiblings.isEmpty()) {
                    sb.append("Closed siblings (parent #%d %s):\n".formatted(parent.id(), parent.subject()));
                    for (var sibling : closedSiblings) {
                        appendClosedIssue(sb, sibling, "sibling");
                    }
                    sb.append("\n");
                }
            }
        }

        // 3. Similar closed issues (same project + tracker, optionally same version)
        if (foundIds.size() - 1 < maxResults && issue.project() != null) {
            ProgressSupport.report(context, 2, 3, "Scanning similar closed issues");
            int remaining = maxResults - (foundIds.size() - 1);
            Integer trackerId = issue.tracker() != null ? issue.tracker().id() : null;
            Integer versionId = issue.fixedVersion() != null ? issue.fixedVersion().id() : null;

            // Try same version first
            var similar = new ArrayList<RedmineIssue>();
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

            if (!similar.isEmpty()) {
                sb.append("Similar closed issues (same project/tracker):\n");
                for (var s : similar) {
                    appendClosedIssue(sb, s, null);
                }
                sb.append("\n");
            }
        }

        int totalFound = foundIds.size() - 1;
        if (totalFound == 0) {
            sb.append("No related closed issues found.\n");
        } else {
            sb.append("Total: %d closed issues found\n".formatted(totalFound));
        }
        ProgressSupport.done(context, "Related closed issue search ready");

        return sb.toString();
    }

    public String findRelatedClosedIssues(int issueId, Integer limit) {
        return findRelatedClosedIssues(issueId, limit, null);
    }

    // ── findLatestAttachment ──────────────────────────────────────────

    @McpTool(description = "Find the latest version of a document/attachment by filename pattern. " +
            "Searches across the issue itself, its parent, siblings, and related issues. " +
            "Returns all matching attachments sorted by date (newest first). " +
            "Useful for finding the latest spec, requirements, or design document.")
    public String findLatestAttachment(
            @McpToolParam(description = "Filename pattern to search (case-insensitive substring match, e.g. 'spec', 'ТЗ', 'requirements')") String pattern,
            @McpToolParam(description = "Issue ID to start search from") int issueId,
            @McpToolParam(description = "Also search in project-wide recent issues (true/false, default false)", required = false) Boolean searchProject,
            McpSyncRequestContext context
    ) {
        ProgressSupport.report(context, 0, 4, "Loading base issue");
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        String patternLower = pattern.toLowerCase();
        var matches = new ArrayList<AttachmentMatch>();

        // Search in the issue itself
        collectAttachmentMatches(issue, patternLower, "issue #%d".formatted(issueId), matches);

        // Search in parent
        if (issue.parent() != null) {
            ProgressSupport.report(context, 1, 4, "Scanning parent and sibling attachments");
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
            ProgressSupport.report(context, 2, 4, "Scanning related issues");
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
            ProgressSupport.report(context, 3, 4, "Scanning recent project issues");
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

        ProgressSupport.report(context, 4, 4, "Sorting matches");
        // Sort by date descending (newest first)
        matches.sort((a, b) -> {
            if (a.attachment.createdOn() == null) return 1;
            if (b.attachment.createdOn() == null) return -1;
            return b.attachment.createdOn().compareTo(a.attachment.createdOn());
        });

        // Deduplicate by attachment ID
        var seen = new java.util.HashSet<Integer>();
        matches.removeIf(m -> !seen.add(m.attachment.id()));

        var sb = new StringBuilder();
        sb.append("Attachments matching \"%s\" (from issue #%d context)\n\n".formatted(pattern, issueId));

        if (matches.isEmpty()) {
            sb.append("No matching attachments found.\n");
            return sb.toString();
        }

        sb.append("Found %d attachments (newest first):\n\n".formatted(matches.size()));

        // Mark the latest
        boolean first = true;
        for (var match : matches) {
            var att = match.attachment;
            if (first) {
                sb.append(">>> LATEST:\n");
                first = false;
            }
            sb.append("  [%d] %s (%s, %s)\n".formatted(
                    att.id(), att.filename(),
                    att.contentType() != null ? att.contentType() : "?",
                    formatSize(att.filesize())));
            sb.append("  Source: %s | Date: %s | Author: %s\n".formatted(
                    match.source,
                    att.createdOn() != null ? att.createdOn().substring(0, Math.min(10, att.createdOn().length())) : "?",
                    att.author() != null ? att.author().name() : "?"));
            sb.append("\n");
        }

        ProgressSupport.done(context, "Attachment search complete");
        return sb.toString();
    }

    public String findLatestAttachment(String pattern, int issueId, Boolean searchProject) {
        return findLatestAttachment(pattern, issueId, searchProject, null);
    }

    // ── getIssueNetwork ───────────────────────────────────────────────

    @McpTool(description = "Build a full network of all relation types for an issue. " +
            "Unlike getIssueTree (parent/child only), this traverses ALL relation types: " +
            "relates, blocks/blocked_by, precedes/follows, duplicates, copied_to. " +
            "Shows each related issue with status, assignee, and due date. " +
            "Follows relations up to the specified depth.")
    public String getIssueNetwork(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How many levels of relations to follow, default 2, max 3", required = false) Integer depth,
            McpSyncRequestContext context
    ) {
        int maxDepth = depth != null ? Math.min(Math.max(depth, 1), 3) : 2;
        int maxIssues = 40;

        var visited = new java.util.LinkedHashMap<Integer, RedmineIssue>();
        var edges = new ArrayList<NetworkEdge>();

        // Fetch root
        ProgressSupport.stage(context, "Loading issue network root");
        var root = client.getIssueForTree(issueId);
        if (root == null) {
            return "Issue #%d not found".formatted(issueId);
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
            ProgressSupport.report(context, visited.size(), maxIssues,
                    "Traversing issue network at depth %d".formatted(currentDepth));

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

        // Format output
        var sb = new StringBuilder();
        sb.append("Issue network for #%d: %s\n".formatted(issueId, root.subject()));
        sb.append("Nodes: %d issues, Edges: %d relations (depth: %d)\n\n".formatted(
                visited.size(), edges.size(), maxDepth));

        // Group edges by type
        var edgesByType = edges.stream()
                .collect(Collectors.groupingBy(e -> e.type, LinkedHashMap::new, Collectors.toList()));

        edgesByType.forEach((type, typeEdges) -> {
            sb.append("%s (%d):\n".formatted(type, typeEdges.size()));
            for (var edge : typeEdges) {
                var from = visited.get(edge.fromId);
                var to = visited.get(edge.toId);
                String fromLabel = from != null ? "#%d %s".formatted(from.id(), from.subject()) : "#%d".formatted(edge.fromId);
                String toLabel = to != null ? "#%d %s".formatted(to.id(), to.subject()) : "#%d".formatted(edge.toId);
                sb.append("  %s \u2192 %s".formatted(fromLabel, toLabel));
                if (edge.delay != null && edge.delay != 0) sb.append(" (delay: %d days)".formatted(edge.delay));
                sb.append("\n");
            }
            sb.append("\n");
        });

        // Issue index with status/assignee
        sb.append("--- Issue index ---\n");
        for (var entry : visited.entrySet()) {
            var iss = entry.getValue();
            boolean isCurrent = iss.id() == issueId;
            sb.append("  #%-5d [%s] %s".formatted(iss.id(), name(iss.status()), name(iss.priority())));
            if (iss.assignedTo() != null) sb.append(" | %s".formatted(iss.assignedTo().name()));
            if (iss.dueDate() != null) sb.append(" | due:%s".formatted(iss.dueDate()));
            if (isCurrent) sb.append("  \u2190 current");
            sb.append("\n");
        }
        ProgressSupport.done(context, "Issue network ready");

        return sb.toString();
    }

    public String getIssueNetwork(int issueId, Integer depth) {
        return getIssueNetwork(issueId, depth, null);
    }

    // --- Helpers for findRelatedClosedIssues ---

    private void appendClosedIssue(StringBuilder sb, RedmineIssue issue, String relationType) {
        sb.append("  #%d %s [%s]".formatted(issue.id(), issue.subject(), name(issue.status())));
        if (relationType != null) sb.append(" (%s)".formatted(relationType));
        if (issue.assignedTo() != null) sb.append(" \u2014 %s".formatted(issue.assignedTo().name()));
        sb.append("\n");
        if (issue.description() != null && !issue.description().isBlank()) {
            String desc = issue.description().replace("\n", " ").strip();
            if (desc.length() > 200) desc = desc.substring(0, 200) + "...";
            sb.append("    %s\n".formatted(desc));
        }
    }

    // --- Helpers for findLatestAttachment ---

    private record AttachmentMatch(RedmineAttachment attachment, String source) {
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

    private record NetworkEdge(int fromId, int toId, String type, Integer delay) {
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

    private void appendCustomFields(StringBuilder sb, List<RedmineIssue.CustomField> customFields) {
        if (customFields == null) {
            return;
        }

        var nonEmptyFields = customFields.stream()
                .filter(cf -> cf != null && !cf.isEmpty())
                .toList();
        if (nonEmptyFields.isEmpty()) {
            return;
        }

        sb.append("Custom fields:\n");
        for (var cf : nonEmptyFields) {
            sb.append("  [%d] %s: %s\n".formatted(cf.id(), cf.name(), cf.displayValue()));
        }
    }

    // --- Status helpers ---

    private boolean isClosedStatus(IdName status) {
        if (status == null) return false;
        String lower = status.name().toLowerCase();
        return lower.contains("closed") || lower.contains("rejected")
                || lower.contains("resolved") || lower.contains("done");
    }

    // --- Formatting helpers ---

    private String name(IdName idName) {
        return idName != null ? idName.name() : "\u2014";
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null) return "?";
        if (timestamp.length() >= 16) {
            return timestamp.substring(0, 10) + " " + timestamp.substring(11, 16);
        }
        return timestamp;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
