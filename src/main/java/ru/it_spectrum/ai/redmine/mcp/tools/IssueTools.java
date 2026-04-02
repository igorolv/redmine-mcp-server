package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import java.nio.charset.StandardCharsets;

@Service
public class IssueTools {
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
