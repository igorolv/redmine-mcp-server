package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.service.IssueHistoryView;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueTreeView;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IssueTools {

    private final RedmineClient client;
    private final IssueService issueService;

    public IssueTools(RedmineClient client, IssueService issueService) {
        this.client = client;
        this.issueService = issueService;
    }

    @McpTool(description = "List issues in Redmine with flexible filtering by project, status, tracker, " +
            "assignee, priority, version, or saved query. Use statusId='*' to include closed issues. " +
            "Use queryId to apply a saved Redmine query (custom filter) — get available IDs via listQueries. " +
            "Use customFieldFilters to pass native Redmine filters like 'cf_10=rtk&cf_3=502167'. " +
            "Supports sorting and pagination.")
    public String listIssues(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open, closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Tracker ID to filter by (optional)", required = false) Integer trackerId,
            @McpToolParam(description = "Assigned user ID to filter by (optional)", required = false) Integer assignedToId,
            @McpToolParam(description = "Priority ID to filter by (optional)", required = false) Integer priorityId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId,
            @McpToolParam(description = "Saved query ID to apply (optional). Use listQueries to find available queries.", required = false) Integer queryId,
            @McpToolParam(description = "Custom field filters in query-string form, e.g. 'cf_10=rtk&cf_3=502167' (optional)", required = false) String customFieldFilters,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;
        Map<String, String> parsedCustomFieldFilters;
        try {
            parsedCustomFieldFilters = parseCustomFieldFilters(customFieldFilters);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        var page = issueService.list(projectId, statusId, trackerId, assignedToId,
                priorityId, versionId, queryId, parsedCustomFieldFilters, sort, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Issues: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.issues().size()));
        for (var issue : page.issues()) {
            appendIssueSummary(sb, issue);
            sb.append("\n");
        }
        return sb.toString();
    }

    public String listIssues(String projectId, String statusId, Integer trackerId,
                             Integer assignedToId, Integer priorityId, Integer versionId,
                             Integer queryId, String sort, Integer limit, Integer offset) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                queryId, null, sort, limit, offset);
    }

    @McpTool(description = "Search across all Redmine content — issues, wiki pages, news, documents, etc. " +
            "Returns results of all types with title, type, URL, and description excerpt. " +
            "Use searchIssues if you only need issues with full details.")
    public String searchAll(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = client.search(query, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Search results for '%s': %d total (showing %d-%d)\n\n".formatted(
                query, result.totalCount(), result.offset() + 1,
                result.offset() + result.results().size()));
        for (var item : result.results()) {
            sb.append("[%s] #%d %s\n".formatted(item.type(), item.id(), item.title()));
            if (item.description() != null && !item.description().isBlank()) {
                sb.append("  %s\n".formatted(item.description().length() > 150
                        ? item.description().substring(0, 150) + "..."
                        : item.description()));
            }
            sb.append("  %s | %s\n\n".formatted(item.url(), item.datetime()));
        }
        return sb.toString();
    }

    @McpTool(description = "Search for issues in Redmine using full-text search. " +
            "Returns a list of matching issues with their details (subject, status, assignee, etc). " +
            "Supports pagination via offset/limit parameters.")
    public String searchIssues(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier to limit search scope (optional)", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = issueService.searchIssues(query, projectId, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Found %d total results (showing %d-%d)\n\n".formatted(
                result.totalCount(), result.offset() + 1,
                result.offset() + result.issues().size()));
        for (var issue : result.issues()) {
            appendIssueSummary(sb, issue);
            sb.append("\n");
        }
        return sb.toString();
    }

    @McpTool(description = "List issues assigned to the currently authenticated user. " +
            "Convenient shortcut — no need to call getCurrentUser first. " +
            "Supports filtering by project, status, and sorting. Uses statusId='open' by default.")
    public String getMyIssues(
            @McpToolParam(description = "Project identifier to filter by (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open (default), closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var maybeResult = issueService.getMyIssues(projectId, statusId, sort, actualOffset, actualLimit);
        if (maybeResult.isEmpty()) {
            return "Could not retrieve current user";
        }
        var result = maybeResult.get();
        var user = result.user();
        var page = result.page();

        var sb = new StringBuilder();
        sb.append("My issues (%s, %d total, showing %d-%d):\n\n".formatted(
                user.firstname() + " " + user.lastname(),
                page.totalCount(), page.offset() + 1,
                page.offset() + page.issues().size()));
        for (var issue : page.issues()) {
            appendIssueSummary(sb, issue);
            sb.append("\n");
        }
        return sb.toString();
    }

    @McpTool(description = "Build a full issue dependency tree: parent chain up to root, " +
            "subtasks down to specified depth, and direct relations. " +
            "Shows hierarchy with status and assignee for each node. " +
            "Useful for understanding task breakdown and dependencies at a glance.")
    public String getIssueTree(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How deep to traverse children, default 2, max 5", required = false) Integer depth,
            McpSyncRequestContext context
    ) {
        IssueTreeView view;
        try {
            view = issueService.getTree(issueId, depth, ProgressSupport.reporterFor(context));
        } catch (IssueNotFoundException e) {
            return "Issue #%d not found".formatted(e.issueId());
        }
        return renderTree(view, depth);
    }

    public String getIssueTree(int issueId, Integer depth) {
        return getIssueTree(issueId, depth, null);
    }

    @McpTool(description = "Get the full change history of a Redmine issue. " +
            "Parses journal entries to show a timeline of status transitions, assignment changes, " +
            "priority changes, and other field modifications with human-readable names. " +
            "Also computes time spent in each status.")
    public String getIssueHistory(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        IssueHistoryView view;
        try {
            view = issueService.getHistory(issueId);
        } catch (IssueNotFoundException e) {
            return "Issue #%d not found".formatted(e.issueId());
        }
        return renderHistory(view);
    }

    @McpTool(description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, " +
            "subtasks (children), relations, notes (journals), and attachments list.")
    public String getIssue(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var maybeIssue = issueService.find(issueId);
        if (maybeIssue.isEmpty()) {
            return "Issue #%d not found".formatted(issueId);
        }
        var sb = new StringBuilder();
        appendIssueDetails(sb, maybeIssue.get());
        return sb.toString();
    }

    // --- Listing formatters ---

    private void appendIssueSummary(StringBuilder sb, RedmineIssue issue) {
        sb.append("#%d  %s\n".formatted(issue.id(), issue.subject()));
        sb.append("  Status: %s | Priority: %s".formatted(
                name(issue.status()), name(issue.priority())));
        if (issue.assignedTo() != null) {
            sb.append(" | Assigned: %s".formatted(issue.assignedTo().name()));
        }
        sb.append(" | Updated: %s\n".formatted(issue.updatedOn()));
    }

    private void appendIssueDetails(StringBuilder sb, RedmineIssue issue) {
        sb.append("Issue #%d: %s\n".formatted(issue.id(), issue.subject()));
        if (issue.isPrivate()) sb.append("[PRIVATE]\n");
        sb.append("Project: %s\n".formatted(name(issue.project())));
        sb.append("Tracker: %s | Status: %s | Priority: %s\n".formatted(
                name(issue.tracker()), name(issue.status()), name(issue.priority())));
        sb.append("Author: %s\n".formatted(name(issue.author())));
        if (issue.assignedTo() != null) {
            sb.append("Assigned to: %s\n".formatted(issue.assignedTo().name()));
        }
        if (issue.parent() != null) {
            sb.append("Parent: #%d %s\n".formatted(issue.parent().id(), issue.parent().name()));
        }
        if (issue.fixedVersion() != null) {
            sb.append("Target version: %s\n".formatted(issue.fixedVersion().name()));
        }
        if (issue.category() != null) {
            sb.append("Category: %s\n".formatted(issue.category().name()));
        }
        if (issue.startDate() != null) sb.append("Start date: %s\n".formatted(issue.startDate()));
        if (issue.dueDate() != null) sb.append("Due date: %s\n".formatted(issue.dueDate()));
        sb.append("Done: %d%%\n".formatted(issue.doneRatio()));
        if (issue.estimatedHours() != null) sb.append("Estimated: %.1f h\n".formatted(issue.estimatedHours()));
        if (issue.spentHours() != null && issue.spentHours() > 0) sb.append("Spent: %.1f h\n".formatted(issue.spentHours()));
        sb.append("Created: %s | Updated: %s\n".formatted(issue.createdOn(), issue.updatedOn()));

        if (issue.description() != null && !issue.description().isBlank()) {
            sb.append("\n--- Description ---\n%s\n".formatted(issue.description()));
        }

        appendCustomFields(sb, issue.customFields());

        if (issue.relations() != null && !issue.relations().isEmpty()) {
            sb.append("\nRelations:\n");
            for (var rel : issue.relations()) {
                int relatedId = rel.issueId() == issue.id() ? rel.issueToId() : rel.issueId();
                sb.append("  %s #%d".formatted(rel.relationType(), relatedId));
                if (rel.delay() != null && rel.delay() != 0) {
                    sb.append(" (delay: %d days)".formatted(rel.delay()));
                }
                sb.append("\n");
            }
        }

        if (issue.children() != null && !issue.children().isEmpty()) {
            sb.append("\nSubtasks (%d):\n".formatted(issue.children().size()));
            for (var child : issue.children()) {
                sb.append("  - #%d %s".formatted(child.id(), child.subject()));
                if (child.tracker() != null) {
                    sb.append(" [%s]".formatted(child.tracker().name()));
                }
                sb.append("\n");
            }
        }

        if (issue.journals() != null && !issue.journals().isEmpty()) {
            var notes = issue.journals().stream()
                    .filter(j -> j.notes() != null && !j.notes().isBlank())
                    .toList();
            if (!notes.isEmpty()) {
                sb.append("\nNotes (%d):\n".formatted(notes.size()));
                for (var journal : notes) {
                    sb.append("\n  [%s] %s:\n  %s\n".formatted(
                            journal.createdOn(),
                            journal.user() != null ? journal.user().name() : "unknown",
                            journal.notes().replace("\n", "\n  ")));
                }
            }
        }

        if (issue.attachments() != null && !issue.attachments().isEmpty()) {
            sb.append("\nAttachments (%d):\n".formatted(issue.attachments().size()));
            for (var att : issue.attachments()) {
                sb.append("  - [%d] %s (%s)\n".formatted(att.id(), att.filename(), formatSize(att.filesize())));
            }

            // Auto-load markdown attachments inline
            for (var att : issue.attachments()) {
                if (isMarkdown(att.filename())) {
                    byte[] content = client.downloadAttachment(att.contentUrl());
                    if (content != null) {
                        sb.append("\n--- %s ---\n".formatted(att.filename()));
                        sb.append(new String(content, StandardCharsets.UTF_8));
                        sb.append("\n");
                    }
                }
            }
        }
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
        sb.append("\nCustom fields:\n");
        for (var cf : nonEmptyFields) {
            sb.append("  [%d] %s: %s\n".formatted(cf.id(), cf.name(), cf.displayValue()));
        }
    }

    // --- Tree formatter ---

    private String renderTree(IssueTreeView view, Integer requestedDepth) {
        int actualDepth = requestedDepth != null
                ? Math.min(Math.max(requestedDepth, 0), IssueService.MAX_TREE_DEPTH)
                : IssueService.DEFAULT_TREE_DEPTH;
        int currentIssueId = view.root().id();

        var sb = new StringBuilder();
        sb.append("Issue tree for #%d: %s\n".formatted(view.root().id(), view.root().subject()));

        if (!view.ancestors().isEmpty()) {
            sb.append("\nParent chain:\n");
            int chainSize = view.ancestors().size() + 1;
            for (int level = 0; level < chainSize; level++) {
                RedmineIssue issue = level < chainSize - 1
                        ? view.ancestors().get(view.ancestors().size() - 1 - level)
                        : view.root();
                String indent = "  ".repeat(level);
                String prefix = level > 0 ? indent + "└─ " : indent;
                sb.append(prefix);
                appendTreeIssueLine(sb, issue, issue.id() == currentIssueId);
            }
        }

        sb.append("\nSubtree (depth %d):\n".formatted(actualDepth));
        appendSubtreeNode(sb, view.subtree(), 0, "", true, currentIssueId);

        if (!view.relations().isEmpty()) {
            sb.append("\nRelations:\n");
            for (var rel : view.relations()) {
                int relatedId = rel.issueId() == currentIssueId ? rel.issueToId() : rel.issueId();
                if (rel.issueId() == currentIssueId) {
                    sb.append("  #%d %s #%d".formatted(currentIssueId, rel.relationType(), relatedId));
                } else {
                    sb.append("  #%d %s #%d".formatted(relatedId, rel.relationType(), currentIssueId));
                }
                if (rel.delay() != null && rel.delay() != 0) {
                    sb.append(" (delay: %d days)".formatted(rel.delay()));
                }
                sb.append("\n");
            }
        }

        sb.append("\nSummary: %d issues loaded".formatted(view.fetchedCount()));
        if (view.limitReached()) {
            sb.append(" (limit reached, tree may be incomplete)");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void appendTreeIssueLine(StringBuilder sb, RedmineIssue issue, boolean isCurrent) {
        sb.append("#%d %s [%s] %s".formatted(
                issue.id(), issue.subject(), name(issue.tracker()), name(issue.status())));
        if (issue.assignedTo() != null) {
            sb.append(" (%s)".formatted(issue.assignedTo().name()));
        }
        if (isCurrent) {
            sb.append(" ← current");
        }
        sb.append("\n");
    }

    private void appendTreeNodeLine(StringBuilder sb, IssueTreeView.TreeNode node, boolean isCurrent) {
        if (node.stub()) {
            sb.append("#%d %s".formatted(node.id(), node.subject()));
            if (node.tracker() != null) {
                sb.append(" [%s]".formatted(node.tracker().name()));
            }
            sb.append("\n");
            return;
        }
        sb.append("#%d %s [%s] %s".formatted(
                node.id(), node.subject(), name(node.tracker()), name(node.status())));
        if (node.assignedTo() != null) {
            sb.append(" (%s)".formatted(node.assignedTo().name()));
        }
        if (isCurrent) {
            sb.append(" ← current");
        }
        sb.append("\n");
    }

    private void appendSubtreeNode(StringBuilder sb, IssueTreeView.TreeNode node, int currentDepth,
                                   String prefix, boolean isLast, int currentIssueId) {
        String connector = currentDepth == 0 ? "  " : (isLast ? "└─ " : "├─ ");
        sb.append(prefix).append(connector);
        appendTreeNodeLine(sb, node, node.id() == currentIssueId);

        if (node.children() == null || node.children().isEmpty()) {
            return;
        }
        String childPrefix = currentDepth == 0 ? "  " : prefix + (isLast ? "   " : "│  ");
        var children = node.children();
        for (int i = 0; i < children.size(); i++) {
            appendSubtreeNode(sb, children.get(i), currentDepth + 1, childPrefix,
                    i == children.size() - 1, currentIssueId);
        }
    }

    // --- History formatter ---

    private String renderHistory(IssueHistoryView view) {
        var issue = view.issue();
        var sb = new StringBuilder();
        sb.append("History of #%d: %s\n".formatted(issue.id(), issue.subject()));
        sb.append("Project: %s | Tracker: %s | Current status: %s\n".formatted(
                name(issue.project()), name(issue.tracker()), name(issue.status())));

        sb.append("\nTimeline:\n");
        boolean firstEntry = true;
        for (var entry : view.timeline()) {
            if (!firstEntry && entry.kind() == IssueHistoryView.Kind.UPDATED) {
                sb.append("\n");
            }
            firstEntry = false;
            sb.append("  %s  [%s] by %s\n".formatted(
                    formatTimestamp(entry.timestamp()),
                    entry.kind() == IssueHistoryView.Kind.CREATED ? "Created" : "Updated",
                    entry.actor()));
            if (entry.kind() == IssueHistoryView.Kind.CREATED) {
                appendCreatedChanges(sb, entry.changes());
            } else {
                for (var change : entry.changes()) {
                    sb.append("    %s\n".formatted(formatFieldChange(change)));
                }
            }
            if (entry.note() != null) {
                String note = entry.note().length() > 200
                        ? entry.note().substring(0, 200) + "..."
                        : entry.note();
                sb.append("    Note: \"%s\"\n".formatted(note.replace("\n", " ")));
            }
        }

        if (!view.statusDurations().isEmpty()) {
            sb.append("\nStatus durations:\n");
            for (var d : view.statusDurations()) {
                String to = d.toTimestamp() != null ? formatTimestamp(d.toTimestamp()) : "present";
                sb.append("  %-16s %s → %s (%s)\n".formatted(
                        d.statusName() + ":",
                        formatTimestamp(d.fromTimestamp()),
                        to,
                        d.duration()));
            }
        }

        return sb.toString();
    }

    private void appendCreatedChanges(StringBuilder sb, List<IssueHistoryView.FieldChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        sb.append("    ");
        boolean first = true;
        for (var change : changes) {
            if (!first) sb.append(" | ");
            sb.append("%s: %s".formatted(change.fieldLabel(), change.newValue()));
            first = false;
        }
        sb.append("\n");
    }

    private String formatFieldChange(IssueHistoryView.FieldChange change) {
        if (change.oldValue() != null && change.newValue() != null) {
            return "%s: %s → %s".formatted(change.fieldLabel(), change.oldValue(), change.newValue());
        }
        if (change.newValue() != null) {
            return "%s: set to %s".formatted(change.fieldLabel(), change.newValue());
        }
        return "%s: %s cleared".formatted(change.fieldLabel(), change.oldValue());
    }

    // --- Common helpers ---

    private Map<String, String> parseCustomFieldFilters(String customFieldFilters) {
        if (customFieldFilters == null || customFieldFilters.isBlank()) {
            return Map.of();
        }
        var filters = new LinkedHashMap<String, String>();
        for (String token : customFieldFilters.split("[&\\r\\n]+")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex == trimmed.length() - 1) {
                throw new IllegalArgumentException(
                        "Invalid customFieldFilters token '%s'. Use format like 'cf_10=rtk&cf_3=502167'."
                                .formatted(trimmed));
            }
            String key = decodeQueryToken(trimmed.substring(0, separatorIndex));
            String value = decodeQueryToken(trimmed.substring(separatorIndex + 1));
            if (!key.matches("cf_\\d+")) {
                throw new IllegalArgumentException(
                        "Invalid custom field key '%s'. Expected keys like cf_10.".formatted(key));
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid custom field value for '%s'. Expected a non-empty value.".formatted(key));
            }
            filters.put(key, value);
        }
        return filters;
    }

    private String decodeQueryToken(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null) return "?";
        if (timestamp.length() >= 16) {
            return timestamp.substring(0, 10) + " " + timestamp.substring(11, 16);
        }
        return timestamp;
    }

    private String name(IdName idName) {
        return idName != null ? idName.name() : "—";
    }

    private boolean isMarkdown(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".md");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
