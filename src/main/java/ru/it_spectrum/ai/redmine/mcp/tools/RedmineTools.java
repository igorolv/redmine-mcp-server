package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;

import java.nio.charset.StandardCharsets;

@Service
public class RedmineTools {

    private final RedmineClient client;

    public RedmineTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "List all projects available in Redmine. " +
            "Returns project names, identifiers, and descriptions. Supports pagination.")
    public String listProjects(
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getProjects(actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Projects: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.projects().size()));

        for (var project : page.projects()) {
            sb.append("- %s [%s] (id: %d)\n".formatted(project.name(), project.identifier(), project.id()));
            if (project.description() != null && !project.description().isBlank()) {
                sb.append("  %s\n".formatted(project.description().length() > 100
                        ? project.description().substring(0, 100) + "..."
                        : project.description()));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get detailed information about a specific Redmine project. " +
            "Returns project name, description, trackers, enabled modules, and other details.")
    public String getProject(
            @McpToolParam(description = "Project identifier (string slug) or numeric ID") String projectId
    ) {
        var project = client.getProject(projectId);
        if (project == null) {
            return "Project '%s' not found".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Project: %s\n".formatted(project.name()));
        sb.append("Identifier: %s\n".formatted(project.identifier()));
        sb.append("ID: %d\n".formatted(project.id()));
        if (project.parent() != null) {
            sb.append("Parent: %s\n".formatted(project.parent().name()));
        }
        if (project.description() != null && !project.description().isBlank()) {
            sb.append("Description: %s\n".formatted(project.description()));
        }
        if (project.homepage() != null && !project.homepage().isBlank()) {
            sb.append("Homepage: %s\n".formatted(project.homepage()));
        }
        sb.append("Public: %s\n".formatted(project.isPublic() ? "yes" : "no"));
        sb.append("Created: %s | Updated: %s\n".formatted(project.createdOn(), project.updatedOn()));

        if (project.trackers() != null && !project.trackers().isEmpty()) {
            sb.append("\nTrackers: %s\n".formatted(
                    project.trackers().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")));
        }
        if (project.enabledModules() != null && !project.enabledModules().isEmpty()) {
            sb.append("Modules: %s\n".formatted(
                    project.enabledModules().stream().map(RedmineProject.NameOnly::name).reduce((a, b) -> a + ", " + b).orElse("")));
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

    @McpTool(description = "Get detailed information about a specific Redmine issue by its ID. " +
            "Returns full issue details including description, status, assignee, dates, and attachments list.")
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

    @McpTool(description = "List all attachments for a specific Redmine issue. " +
            "Returns attachment names, sizes, content types, and IDs that can be used with getAttachmentContent.")
    public String listAttachments(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var attachments = issue.attachments();
        if (attachments == null || attachments.isEmpty()) {
            return "Issue #%d has no attachments".formatted(issueId);
        }

        var sb = new StringBuilder();
        sb.append("Attachments for issue #%d (%d files):\n\n".formatted(issueId, attachments.size()));

        for (var att : attachments) {
            sb.append("- [%d] %s (%s, %s)\n".formatted(
                    att.id(), att.filename(), att.contentType(), formatSize(att.filesize())));
            if (att.description() != null && !att.description().isBlank()) {
                sb.append("  Description: %s\n".formatted(att.description()));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get the content of a text-based attachment from Redmine. " +
            "Works with text files, logs, XML, JSON, CSV, etc. " +
            "For binary files (images, PDFs) returns only metadata. " +
            "Use listAttachments first to get the attachment ID.")
    public String getAttachmentContent(
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            return "Attachment #%d not found".formatted(attachmentId);
        }

        var sb = new StringBuilder();
        sb.append("Attachment: %s\n".formatted(attachment.filename()));
        sb.append("Type: %s, Size: %s\n".formatted(attachment.contentType(), formatSize(attachment.filesize())));
        sb.append("Created: %s by %s\n\n".formatted(attachment.createdOn(),
                attachment.author() != null ? attachment.author().name() : "unknown"));

        if (isTextContent(attachment.contentType(), attachment.filename())) {
            byte[] content = client.downloadAttachment(attachment.contentUrl());
            if (content != null) {
                sb.append("--- Content ---\n");
                sb.append(new String(content, StandardCharsets.UTF_8));
            }
        } else {
            sb.append("Binary file — content not displayed. Content URL: %s\n".formatted(attachment.contentUrl()));
        }

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
        sb.append("Project: %s\n".formatted(name(issue.project())));
        sb.append("Tracker: %s | Status: %s | Priority: %s\n".formatted(
                name(issue.tracker()), name(issue.status()), name(issue.priority())));
        sb.append("Author: %s\n".formatted(name(issue.author())));
        if (issue.assignedTo() != null) {
            sb.append("Assigned to: %s\n".formatted(issue.assignedTo().name()));
        }
        if (issue.startDate() != null) sb.append("Start date: %s\n".formatted(issue.startDate()));
        if (issue.dueDate() != null) sb.append("Due date: %s\n".formatted(issue.dueDate()));
        sb.append("Done: %d%%\n".formatted(issue.doneRatio()));
        sb.append("Created: %s | Updated: %s\n".formatted(issue.createdOn(), issue.updatedOn()));

        if (issue.description() != null && !issue.description().isBlank()) {
            sb.append("\n--- Description ---\n%s\n".formatted(issue.description()));
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
        }
    }

    private String name(IdName idName) {
        return idName != null ? idName.name() : "—";
    }

    private boolean isTextContent(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.startsWith("text/")) return true;
            if (contentType.contains("json") || contentType.contains("xml") || contentType.contains("csv")) return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv")
                    || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yml")
                    || lower.endsWith(".yaml") || lower.endsWith(".sql") || lower.endsWith(".md")
                    || lower.endsWith(".html") || lower.endsWith(".properties") || lower.endsWith(".conf");
        }
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
