package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IssueTools {
    private static final int MAX_TREE_DEPTH = 5;
    private static final int MAX_TREE_ISSUES = 50;
    private static final int DEFAULT_TREE_DEPTH = 2;

    private final RedmineClient client;

    public IssueTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "List issues in Redmine with flexible filtering by project, status, tracker, " +
            "assignee, priority, version, or saved query. Use statusId='*' to include closed issues. " +
            "Use queryId to apply a saved Redmine query (custom filter) — get available IDs via listQueries. " +
            "Supports sorting and pagination.")
    public String listIssues(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Status filter: open, closed, * (all), or numeric status ID (optional)", required = false) String statusId,
            @McpToolParam(description = "Tracker ID to filter by (optional)", required = false) Integer trackerId,
            @McpToolParam(description = "Assigned user ID to filter by (optional)", required = false) Integer assignedToId,
            @McpToolParam(description = "Priority ID to filter by (optional)", required = false) Integer priorityId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId,
            @McpToolParam(description = "Saved query ID to apply (optional). Use listQueries to find available queries.", required = false) Integer queryId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc' (optional)", required = false) String sort,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.listIssues(projectId, statusId, trackerId, assignedToId,
                priorityId, versionId, sort, queryId, actualOffset, actualLimit);

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

        var result = client.searchIssues(query, projectId, actualOffset, actualLimit);

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
        var user = client.getCurrentUser();
        if (user == null) {
            return "Could not retrieve current user";
        }

        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.listIssues(projectId, statusId, null, user.id(),
                null, null, sort, actualOffset, actualLimit);

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
        int actualDepth = depth != null ? Math.min(Math.max(depth, 0), MAX_TREE_DEPTH) : DEFAULT_TREE_DEPTH;

        var visited = new HashSet<Integer>();
        var fetchCount = new int[]{0};
        ProgressSupport.stage(context, "Loading issue tree root");

        RedmineIssue root = fetchForTree(issueId, visited, fetchCount, context, "Loaded tree node");
        if (root == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var sb = new StringBuilder();
        sb.append("Issue tree for #%d: %s\n".formatted(root.id(), root.subject()));

        // Parent chain
        var parents = new ArrayList<RedmineIssue>();
        parents.add(root);
        RedmineIssue current = root;
        ProgressSupport.stage(context, "Loading parent chain");
        while (current.parent() != null && fetchCount[0] < MAX_TREE_ISSUES) {
            RedmineIssue parent = fetchForTree(current.parent().id(), visited, fetchCount, context, "Loaded parent");
            if (parent == null) break;
            parents.add(parent);
            current = parent;
        }

        if (parents.size() > 1) {
            sb.append("\nParent chain:\n");
            for (int i = parents.size() - 1; i >= 0; i--) {
                var issue = parents.get(i);
                String indent = "  ".repeat(parents.size() - 1 - i);
                String prefix = i < parents.size() - 1 ? indent + "\u2514\u2500 " : indent;
                sb.append(prefix);
                appendTreeNode(sb, issue, issue.id() == issueId);
            }
        }

        // Subtree
        ProgressSupport.stage(context, "Loading subtree");
        sb.append("\nSubtree (depth %d):\n".formatted(actualDepth));
        appendSubtree(sb, root, actualDepth, 0, "", true, visited, fetchCount, issueId, context);

        // Relations from the starting issue
        if (root.relations() != null && !root.relations().isEmpty()) {
            ProgressSupport.stage(context, "Formatting relations");
            sb.append("\nRelations:\n");
            for (var rel : root.relations()) {
                int relatedId = rel.issueId() == root.id() ? rel.issueToId() : rel.issueId();
                if (rel.issueId() == root.id()) {
                    sb.append("  #%d %s #%d".formatted(root.id(), rel.relationType(), relatedId));
                } else {
                    sb.append("  #%d %s #%d".formatted(relatedId, rel.relationType(), root.id()));
                }
                if (rel.delay() != null && rel.delay() != 0) {
                    sb.append(" (delay: %d days)".formatted(rel.delay()));
                }
                sb.append("\n");
            }
        }

        sb.append("\nSummary: %d issues loaded".formatted(fetchCount[0]));
        if (fetchCount[0] >= MAX_TREE_ISSUES) {
            sb.append(" (limit reached, tree may be incomplete)");
        }
        sb.append("\n");
        ProgressSupport.done(context, "Issue tree ready");

        return sb.toString();
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
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        // Build reference data maps for name resolution
        var refMaps = buildReferenceMaps(issue);

        var sb = new StringBuilder();
        sb.append("History of #%d: %s\n".formatted(issue.id(), issue.subject()));
        sb.append("Project: %s | Tracker: %s | Current status: %s\n".formatted(
                name(issue.project()), name(issue.tracker()), name(issue.status())));

        sb.append("\nTimeline:\n");

        // Creation entry
        sb.append("  %s  [Created] by %s\n".formatted(
                formatTimestamp(issue.createdOn()), name(issue.author())));
        sb.append("    Status: %s | Priority: %s".formatted(
                name(issue.status()), name(issue.priority())));
        if (issue.assignedTo() != null) {
            sb.append(" | Assigned to: %s".formatted(issue.assignedTo().name()));
        }
        sb.append("\n");

        // Journal entries
        // Track status changes for duration calculation
        var statusChanges = new ArrayList<StatusChange>();
        String initialStatus = findInitialStatus(issue, refMaps);
        statusChanges.add(new StatusChange(initialStatus, issue.createdOn()));

        if (issue.journals() != null) {
            for (var journal : issue.journals()) {
                boolean hasChanges = false;
                var changes = new StringBuilder();

                if (journal.details() != null) {
                    for (var detail : journal.details()) {
                        String line = formatDetail(detail, refMaps);
                        if (line != null) {
                            changes.append("    %s\n".formatted(line));
                            hasChanges = true;
                        }

                        // Track status changes
                        if ("attr".equals(detail.property()) && "status_id".equals(detail.name())
                                && detail.newValue() != null) {
                            String statusName = resolveName(refMaps, "status_id", detail.newValue());
                            statusChanges.add(new StatusChange(statusName, journal.createdOn()));
                        }
                    }
                }

                boolean hasNotes = journal.notes() != null && !journal.notes().isBlank();

                if (hasChanges || hasNotes) {
                    sb.append("\n  %s  [Updated] by %s\n".formatted(
                            formatTimestamp(journal.createdOn()),
                            journal.user() != null ? journal.user().name() : "unknown"));
                    sb.append(changes);
                    if (hasNotes) {
                        String note = journal.notes().length() > 200
                                ? journal.notes().substring(0, 200) + "..."
                                : journal.notes();
                        sb.append("    Note: \"%s\"\n".formatted(note.replace("\n", " ")));
                    }
                }
            }
        }

        // Status durations
        if (statusChanges.size() > 1 || !statusChanges.isEmpty()) {
            sb.append("\nStatus durations:\n");
            for (int i = 0; i < statusChanges.size(); i++) {
                var change = statusChanges.get(i);
                String from = formatTimestamp(change.timestamp);
                String to;
                String duration;

                if (i + 1 < statusChanges.size()) {
                    var next = statusChanges.get(i + 1);
                    to = formatTimestamp(next.timestamp);
                    duration = computeDuration(change.timestamp, next.timestamp);
                } else {
                    to = "present";
                    duration = computeDuration(change.timestamp, null);
                }

                sb.append("  %-16s %s \u2192 %s (%s)\n".formatted(
                        change.statusName + ":", from, to, duration));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, " +
            "subtasks (children), relations, notes (journals), and attachments list.")
    public String getIssue(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var sb = new StringBuilder();
        appendIssueDetails(sb, issue);
        return sb.toString();
    }

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

        if (issue.customFields() != null && !issue.customFields().isEmpty()) {
            sb.append("\nCustom fields:\n");
            for (var cf : issue.customFields()) {
                String val = cf.value() != null ? cf.value().toString() : "";
                if (!val.isBlank() && !"[]".equals(val)) {
                    sb.append("  %s: %s\n".formatted(cf.name(), val));
                }
            }
        }

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

    // --- Tree helpers ---

    private RedmineIssue fetchForTree(int issueId, Set<Integer> visited, int[] fetchCount) {
        return fetchForTree(issueId, visited, fetchCount, null, "Loaded issue");
    }

    private RedmineIssue fetchForTree(int issueId, Set<Integer> visited, int[] fetchCount,
                                      McpSyncRequestContext context, String messagePrefix) {
        if (!visited.add(issueId) || fetchCount[0] >= MAX_TREE_ISSUES) {
            return null;
        }
        fetchCount[0]++;
        ProgressSupport.report(context, fetchCount[0], MAX_TREE_ISSUES,
                "%s %d/%d".formatted(messagePrefix, fetchCount[0], MAX_TREE_ISSUES));
        return client.getIssueForTree(issueId);
    }

    private void appendTreeNode(StringBuilder sb, RedmineIssue issue, boolean isCurrent) {
        sb.append("#%d %s [%s] %s".formatted(
                issue.id(), issue.subject(), name(issue.tracker()), name(issue.status())));
        if (issue.assignedTo() != null) {
            sb.append(" (%s)".formatted(issue.assignedTo().name()));
        }
        if (isCurrent) {
            sb.append(" \u2190 current");
        }
        sb.append("\n");
    }

    private void appendSubtree(StringBuilder sb, RedmineIssue issue, int maxDepth,
                                int currentDepth, String prefix, boolean isLast,
                                Set<Integer> visited, int[] fetchCount, int currentIssueId,
                                McpSyncRequestContext context) {
        String connector = currentDepth == 0 ? "  " : (isLast ? "\u2514\u2500 " : "\u251C\u2500 ");
        sb.append(prefix).append(connector);
        appendTreeNode(sb, issue, issue.id() == currentIssueId);

        if (issue.children() == null || issue.children().isEmpty()) {
            return;
        }

        if (currentDepth >= maxDepth) {
            // Show children as compact stubs at depth boundary
            String childPrefix = currentDepth == 0 ? "  " : prefix + (isLast ? "   " : "\u2502  ");
            for (int i = 0; i < issue.children().size(); i++) {
                var child = issue.children().get(i);
                String childConnector = i == issue.children().size() - 1 ? "\u2514\u2500 " : "\u251C\u2500 ";
                sb.append(childPrefix).append(childConnector);
                sb.append("#%d %s".formatted(child.id(), child.subject()));
                if (child.tracker() != null) {
                    sb.append(" [%s]".formatted(child.tracker().name()));
                }
                sb.append("\n");
            }
            return;
        }

        String childPrefix = currentDepth == 0 ? "  " : prefix + (isLast ? "   " : "\u2502  ");
        var children = issue.children();
        for (int i = 0; i < children.size(); i++) {
            if (fetchCount[0] >= MAX_TREE_ISSUES) {
                sb.append(childPrefix).append("  ... (limit reached)\n");
                break;
            }
            var child = children.get(i);
            RedmineIssue fullChild = fetchForTree(child.id(), visited, fetchCount, context, "Loaded subtree node");
            if (fullChild != null) {
                appendSubtree(sb, fullChild, maxDepth, currentDepth + 1,
                        childPrefix, i == children.size() - 1, visited, fetchCount, currentIssueId, context);
            } else {
                // Already visited or fetch limit reached — show minimal info
                String childConnector = i == children.size() - 1 ? "\u2514\u2500 " : "\u251C\u2500 ";
                sb.append(childPrefix).append(childConnector);
                sb.append("#%d %s".formatted(child.id(), child.subject()));
                if (child.tracker() != null) {
                    sb.append(" [%s]".formatted(child.tracker().name()));
                }
                sb.append("\n");
            }
        }
    }

    // --- History helpers ---

    private record StatusChange(String statusName, String timestamp) {
    }

    private Map<String, Map<String, String>> buildReferenceMaps(RedmineIssue issue) {
        var refMaps = new HashMap<String, Map<String, String>>();

        // Statuses
        var statusMap = new HashMap<String, String>();
        for (var s : client.getIssueStatuses()) {
            statusMap.put(String.valueOf(s.id()), s.name());
        }
        refMaps.put("status_id", statusMap);

        // Priorities
        var priorityMap = new HashMap<String, String>();
        for (var p : client.getIssuePriorities()) {
            priorityMap.put(String.valueOf(p.id()), p.name());
        }
        refMaps.put("priority_id", priorityMap);

        // Trackers
        var trackerMap = new HashMap<String, String>();
        for (var t : client.getTrackers()) {
            trackerMap.put(String.valueOf(t.id()), t.name());
        }
        refMaps.put("tracker_id", trackerMap);

        // Versions (project-specific)
        if (issue.project() != null) {
            var versionMap = new HashMap<String, String>();
            for (var v : client.getProjectVersions(String.valueOf(issue.project().id()))) {
                versionMap.put(String.valueOf(v.id()), v.name());
            }
            refMaps.put("fixed_version_id", versionMap);
        }

        // Users — collect from issue data
        var userMap = new HashMap<String, String>();
        if (issue.author() != null) {
            userMap.put(String.valueOf(issue.author().id()), issue.author().name());
        }
        if (issue.assignedTo() != null) {
            userMap.put(String.valueOf(issue.assignedTo().id()), issue.assignedTo().name());
        }
        if (issue.journals() != null) {
            for (var j : issue.journals()) {
                if (j.user() != null) {
                    userMap.put(String.valueOf(j.user().id()), j.user().name());
                }
            }
        }
        refMaps.put("assigned_to_id", userMap);

        return refMaps;
    }

    private String findInitialStatus(RedmineIssue issue, Map<String, Map<String, String>> refMaps) {
        // Walk journals backwards to find the first status change and get its old_value
        if (issue.journals() != null) {
            for (var journal : issue.journals()) {
                if (journal.details() != null) {
                    for (var detail : journal.details()) {
                        if ("attr".equals(detail.property()) && "status_id".equals(detail.name())
                                && detail.oldValue() != null) {
                            return resolveName(refMaps, "status_id", detail.oldValue());
                        }
                    }
                }
            }
        }
        // No status changes found — current status is the initial status
        return name(issue.status());
    }

    private String formatDetail(RedmineIssue.Detail detail, Map<String, Map<String, String>> refMaps) {
        if (!"attr".equals(detail.property()) && !"cf".equals(detail.property())) {
            return null;
        }

        String fieldName = formatFieldName(detail.name());
        String oldVal = resolveDetailValue(detail.property(), detail.name(), detail.oldValue(), refMaps);
        String newVal = resolveDetailValue(detail.property(), detail.name(), detail.newValue(), refMaps);

        if (oldVal != null && newVal != null) {
            return "%s: %s \u2192 %s".formatted(fieldName, oldVal, newVal);
        } else if (newVal != null) {
            return "%s: set to %s".formatted(fieldName, newVal);
        } else if (oldVal != null) {
            return "%s: %s cleared".formatted(fieldName, oldVal);
        }
        return null;
    }

    private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
            Map.entry("status_id", "Status"),
            Map.entry("assigned_to_id", "Assigned to"),
            Map.entry("priority_id", "Priority"),
            Map.entry("tracker_id", "Tracker"),
            Map.entry("fixed_version_id", "Target version"),
            Map.entry("done_ratio", "Done ratio"),
            Map.entry("subject", "Subject"),
            Map.entry("description", "Description"),
            Map.entry("due_date", "Due date"),
            Map.entry("start_date", "Start date"),
            Map.entry("estimated_hours", "Estimated hours"),
            Map.entry("category_id", "Category"),
            Map.entry("parent_id", "Parent task"),
            Map.entry("is_private", "Private")
    );

    private String formatFieldName(String name) {
        return FIELD_NAMES.getOrDefault(name, name);
    }

    private String resolveDetailValue(String property, String name, String value,
                                       Map<String, Map<String, String>> refMaps) {
        if (value == null) return null;

        if ("attr".equals(property)) {
            var map = refMaps.get(name);
            if (map != null) {
                return map.getOrDefault(value, value);
            }
            // For done_ratio, append %
            if ("done_ratio".equals(name)) {
                return value + "%";
            }
        }
        return value;
    }

    private String resolveName(Map<String, Map<String, String>> refMaps, String field, String id) {
        var map = refMaps.get(field);
        if (map != null) {
            return map.getOrDefault(id, id);
        }
        return id;
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null) return "?";
        // Trim to "YYYY-MM-DD HH:MM" for readability
        if (timestamp.length() >= 16) {
            return timestamp.substring(0, 10) + " " + timestamp.substring(11, 16);
        }
        return timestamp;
    }

    private String computeDuration(String from, String to) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(from, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            OffsetDateTime end = to != null
                    ? OffsetDateTime.parse(to, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : OffsetDateTime.now();
            long days = Duration.between(start, end).toDays();
            if (days == 0) {
                long hours = Duration.between(start, end).toHours();
                return hours <= 1 ? "< 1 hour" : hours + " hours";
            }
            return days == 1 ? "1 day" : days + " days";
        } catch (DateTimeParseException e) {
            return "?";
        }
    }

    // --- Common helpers ---

    private String name(IdName idName) {
        return idName != null ? idName.name() : "\u2014";
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
