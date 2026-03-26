package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

@Service
public class RedmineTools {

    private final RedmineClient client;

    public RedmineTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "Get information about the currently authenticated Redmine user. " +
            "Returns user ID, login, name, email, groups, and project memberships. " +
            "Useful to find your own user ID for filtering (e.g. assigned_to_id in listIssues).")
    public String getCurrentUser() {
        var user = client.getCurrentUser();
        if (user == null) {
            return "Could not retrieve current user";
        }

        var sb = new StringBuilder();
        sb.append("Current user: %s %s\n".formatted(user.firstname(), user.lastname()));
        sb.append("ID: %d | Login: %s\n".formatted(user.id(), user.login()));
        if (user.mail() != null) sb.append("Email: %s\n".formatted(user.mail()));
        sb.append("Last login: %s\n".formatted(user.lastLoginOn()));

        if (user.groups() != null && !user.groups().isEmpty()) {
            sb.append("\nGroups:\n");
            for (var g : user.groups()) {
                sb.append("  - %s\n".formatted(g.name()));
            }
        }

        if (user.memberships() != null && !user.memberships().isEmpty()) {
            sb.append("\nProject memberships:\n");
            for (var m : user.memberships()) {
                if (m.project() != null) {
                    String roles = m.roles() != null
                            ? m.roles().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")
                            : "";
                    sb.append("  - %s — %s\n".formatted(m.project().name(), roles));
                }
            }
        }

        return sb.toString();
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

    @McpTool(description = "List members of a Redmine project with their roles.")
    public String listProjectMembers(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Maximum number of results, default 100", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 100;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getProjectMembers(projectId, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Members of project '%s': %d total\n\n".formatted(projectId, page.totalCount()));

        for (var m : page.memberships()) {
            String member = m.user() != null ? m.user().name() : (m.group() != null ? m.group().name() + " (group)" : "unknown");
            String roles = m.roles() != null
                    ? m.roles().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")
                    : "";
            sb.append("- %s — %s\n".formatted(member, roles));
        }

        return sb.toString();
    }

    @McpTool(description = "List versions (milestones) of a Redmine project. " +
            "Returns version names, statuses, due dates, and descriptions.")
    public String listVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var versions = client.getProjectVersions(projectId);

        if (versions.isEmpty()) {
            return "No versions found for project '%s'".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Versions for project '%s' (%d):\n\n".formatted(projectId, versions.size()));

        for (var v : versions) {
            sb.append("- %s (status: %s".formatted(v.name(), v.status()));
            if (v.dueDate() != null) sb.append(", due: %s".formatted(v.dueDate()));
            sb.append(")\n");
            if (v.description() != null && !v.description().isBlank()) {
                sb.append("  %s\n".formatted(v.description()));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get a wiki page from a Redmine project. " +
            "Returns the page title, content (in Textile/Markdown markup), author, and attachments.")
    public String getWikiPage(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Wiki page title (use 'Wiki' for the start page)") String pageTitle
    ) {
        var page = client.getWikiPage(projectId, pageTitle);
        if (page == null) {
            return "Wiki page '%s' not found in project '%s'".formatted(pageTitle, projectId);
        }

        var sb = new StringBuilder();
        sb.append("Wiki: %s\n".formatted(page.title()));
        if (page.author() != null) sb.append("Author: %s\n".formatted(page.author().name()));
        sb.append("Version: %d | Updated: %s\n".formatted(page.version(), page.updatedOn()));

        if (page.text() != null) {
            sb.append("\n--- Content ---\n%s\n".formatted(page.text()));
        }

        if (page.attachments() != null && !page.attachments().isEmpty()) {
            sb.append("\nAttachments (%d):\n".formatted(page.attachments().size()));
            for (var att : page.attachments()) {
                sb.append("  - [%d] %s (%s)\n".formatted(att.id(), att.filename(), formatSize(att.filesize())));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "List all wiki pages in a Redmine project. " +
            "Returns page titles and dates. Use getWikiPage to read a specific page's content.")
    public String listWikiPages(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var pages = client.getWikiIndex(projectId);
        if (pages.isEmpty()) {
            return "No wiki pages found in project '%s'".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Wiki pages in project '%s' (%d):\n\n".formatted(projectId, pages.size()));
        for (var page : pages) {
            sb.append("- %s".formatted(page.title()));
            if (page.updatedOn() != null) sb.append(" (updated: %s)".formatted(page.updatedOn()));
            sb.append("\n");
        }
        return sb.toString();
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

    @McpTool(description = "List time entries (time tracking / logged hours) in Redmine. " +
            "Filter by project, issue, user, or date range. " +
            "Returns hours, activity type, user, date, and comments.")
    public String listTimeEntries(
            @McpToolParam(description = "Project identifier (optional)", required = false) String projectId,
            @McpToolParam(description = "Issue ID to filter by (optional)", required = false) Integer issueId,
            @McpToolParam(description = "User ID to filter by (optional)", required = false) Integer userId,
            @McpToolParam(description = "From date, YYYY-MM-DD (optional)", required = false) String from,
            @McpToolParam(description = "To date, YYYY-MM-DD (optional)", required = false) String to,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getTimeEntries(projectId, issueId, userId, from, to, actualOffset, actualLimit);

        var sb = new StringBuilder();
        sb.append("Time entries: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.timeEntries().size()));

        for (var entry : page.timeEntries()) {
            sb.append("- %s | %.2f h | %s | %s".formatted(
                    entry.spentOn(), entry.hours(),
                    entry.user() != null ? entry.user().name() : "—",
                    entry.activity() != null ? entry.activity().name() : "—"));
            if (entry.issue() != null) {
                sb.append(" | Issue #%d".formatted(entry.issue().id()));
            }
            if (entry.comments() != null && !entry.comments().isBlank()) {
                sb.append("\n  %s".formatted(entry.comments()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @McpTool(description = "List all available issue statuses in Redmine. " +
            "Returns status IDs and names. Use these IDs for filtering in listIssues.")
    public String listStatuses() {
        var statuses = client.getIssueStatuses();
        if (statuses.isEmpty()) {
            return "No statuses found";
        }
        var sb = new StringBuilder("Issue statuses:\n\n");
        for (var s : statuses) {
            sb.append("- [%d] %s\n".formatted(s.id(), s.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available trackers in Redmine. " +
            "Returns tracker IDs and names. Use these IDs for filtering in listIssues.")
    public String listTrackers() {
        var trackers = client.getTrackers();
        if (trackers.isEmpty()) {
            return "No trackers found";
        }
        var sb = new StringBuilder("Trackers:\n\n");
        for (var t : trackers) {
            sb.append("- [%d] %s\n".formatted(t.id(), t.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available issue priorities in Redmine. " +
            "Returns priority IDs and names. Use these IDs for filtering in listIssues.")
    public String listPriorities() {
        var priorities = client.getIssuePriorities();
        if (priorities.isEmpty()) {
            return "No priorities found";
        }
        var sb = new StringBuilder("Issue priorities:\n\n");
        for (var p : priorities) {
            sb.append("- [%d] %s\n".formatted(p.id(), p.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List issue categories for a specific Redmine project. " +
            "Returns category IDs and names. Categories are project-specific.")
    public String listIssueCategories(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var categories = client.getIssueCategories(projectId);
        if (categories.isEmpty()) {
            return "No issue categories found for project: " + projectId;
        }
        var sb = new StringBuilder("Issue categories for project %s:\n\n".formatted(projectId));
        for (var c : categories) {
            sb.append("- [%d] %s\n".formatted(c.id(), c.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List all available time entry activity types in Redmine. " +
            "Returns activity IDs and names. Use these IDs when logging time entries.")
    public String listTimeEntryActivities() {
        var activities = client.getTimeEntryActivities();
        if (activities.isEmpty()) {
            return "No time entry activities found";
        }
        var sb = new StringBuilder("Time entry activities:\n\n");
        for (var a : activities) {
            sb.append("- [%d] %s\n".formatted(a.id(), a.name()));
        }
        return sb.toString();
    }

    @McpTool(description = "List saved queries (custom filters) available in Redmine. " +
            "Returns query IDs and names. Use the query ID with listIssues(queryId) " +
            "to apply a saved filter — especially useful for queries that use custom fields.")
    public String listQueries(
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getQueries(actualOffset, actualLimit);
        if (page.queries().isEmpty()) {
            return "No saved queries found";
        }

        var sb = new StringBuilder();
        sb.append("Saved queries: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.queries().size()));

        for (var q : page.queries()) {
            sb.append("- [%d] %s".formatted(q.id(), q.name()));
            if (q.projectId() != null) {
                sb.append(" (project #%d)".formatted(q.projectId()));
            }
            if (q.isPublic()) {
                sb.append(" [public]");
            }
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

    @McpTool(description = "Get the content of an attachment from Redmine. " +
            "Supports text files (txt, log, xml, json, csv, etc.), " +
            "PDF, Word (.docx), Excel (.xlsx), and PowerPoint (.pptx). " +
            "For images use getImageAttachment instead. " +
            "For other binary files returns only metadata. " +
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

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (isDocumentContent(ext, contentType)) {
            byte[] content = client.downloadAttachment(attachment.contentUrl());
            if (content != null) {
                sb.append("--- Content ---\n");
                sb.append(truncate(extractDocumentText(ext, contentType, content)));
            }
        } else if (isTextContent(contentType, attachment.filename())) {
            byte[] content = client.downloadAttachment(attachment.contentUrl());
            if (content != null) {
                sb.append("--- Content ---\n");
                sb.append(truncate(new String(content, StandardCharsets.UTF_8)));
            }
        } else {
            sb.append("Binary file — content not displayed. Content URL: %s\n".formatted(attachment.contentUrl()));
        }

        return sb.toString();
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final int DEFAULT_MAX_WIDTH = 1024;

    @McpTool(description = "Download an image attachment from Redmine and return it for visual analysis. " +
            "Supports PNG, JPEG, GIF, BMP, WebP. Automatically resizes large images to save tokens. " +
            "Use listAttachments first to get the attachment ID.")
    public McpSchema.CallToolResult getImageAttachment(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Maximum image width in pixels for resizing (default 1024). " +
                    "Height is scaled proportionally.", required = false) Integer maxWidth
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Attachment #%d not found".formatted(attachmentId))
                    .isError(true)
                    .build();
        }

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (!IMAGE_EXTENSIONS.contains(ext) && !contentType.startsWith("image/")) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Attachment #%d (%s) is not an image. Use getAttachmentContent for text/document files."
                            .formatted(attachmentId, attachment.filename()))
                    .isError(true)
                    .build();
        }

        byte[] imageData = client.downloadAttachment(attachment.contentUrl());
        if (imageData == null) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Failed to download attachment #%d".formatted(attachmentId))
                    .isError(true)
                    .build();
        }

        int actualMaxWidth = maxWidth != null && maxWidth > 0 ? maxWidth : DEFAULT_MAX_WIDTH;

        try {
            String mimeType = contentType.startsWith("image/") ? contentType : "image/" + ext.replace("jpg", "jpeg");
            byte[] processedData = resizeImageIfNeeded(imageData, actualMaxWidth, ext);
            String base64 = Base64.getEncoder().encodeToString(processedData);

            // If resized, output format is always PNG
            if (processedData != imageData) {
                mimeType = "image/png";
            }

            String metadata = "Attachment: %s (%s, %s)".formatted(
                    attachment.filename(), attachment.contentType(), formatSize(attachment.filesize()));

            return McpSchema.CallToolResult.builder()
                    .addTextContent(metadata)
                    .addContent(new McpSchema.ImageContent(null, base64, mimeType))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Failed to process image #%d: %s".formatted(attachmentId, e.getMessage()))
                    .isError(true)
                    .build();
        }
    }

    private byte[] resizeImageIfNeeded(byte[] imageData, int maxWidth, String ext) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            // ImageIO couldn't decode — return raw bytes (e.g. WebP without plugin)
            return imageData;
        }

        if (image.getWidth() <= maxWidth) {
            return imageData;
        }

        int newWidth = maxWidth;
        int newHeight = (int) Math.round((double) image.getHeight() * newWidth / image.getWidth());

        var resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        var out = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", out);
        return out.toByteArray();
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

    private static final int MAX_TEXT_LENGTH = 50_000;

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx");

    private boolean isDocumentContent(String ext, String contentType) {
        if (DOCUMENT_EXTENSIONS.contains(ext)) return true;
        return contentType.equals("application/pdf")
                || contentType.contains("wordprocessingml")
                || contentType.contains("spreadsheetml")
                || contentType.contains("presentationml");
    }

    private String extractDocumentText(String ext, String contentType, byte[] data) {
        try {
            if ("pdf".equals(ext) || contentType.equals("application/pdf")) {
                return extractPdfText(data);
            } else if ("docx".equals(ext) || contentType.contains("wordprocessingml")) {
                return extractDocxText(data);
            } else if ("xlsx".equals(ext) || contentType.contains("spreadsheetml")) {
                return extractXlsxText(data);
            } else if ("pptx".equals(ext) || contentType.contains("presentationml")) {
                return extractPptxText(data);
            }
            return "(unsupported document format)";
        } catch (Exception e) {
            return "(failed to extract text: %s)".formatted(e.getMessage());
        }
    }

    private String extractPdfText(byte[] data) throws Exception {
        try (var doc = Loader.loadPDF(data)) {
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                return "(PDF contains no extractable text — possibly a scanned image)";
            }
            return text;
        }
    }

    private String extractDocxText(byte[] data) throws Exception {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            var sb = new StringBuilder();
            for (var para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            for (var table : doc.getTables()) {
                sb.append("\n[Table]\n");
                for (var row : table.getRows()) {
                    var cells = row.getTableCells().stream()
                            .map(c -> c.getText().strip())
                            .toList();
                    sb.append(String.join(" | ", cells)).append("\n");
                }
            }
            if (sb.isEmpty()) return "(Word document contains no text)";
            return sb.toString();
        }
    }

    private String extractXlsxText(byte[] data) throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sb = new StringBuilder();
            var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                var sheet = wb.getSheetAt(i);
                sb.append("--- Sheet: %s ---\n".formatted(sheet.getSheetName()));
                for (var row : sheet) {
                    var cells = new java.util.ArrayList<String>();
                    for (var cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    if (cells.stream().anyMatch(c -> !c.isEmpty())) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (sb.isEmpty()) return "(Excel file contains no data)";
            return sb.toString();
        }
    }

    private String extractPptxText(byte[] data) throws Exception {
        try (var pptx = new XMLSlideShow(new ByteArrayInputStream(data))) {
            var sb = new StringBuilder();
            int slideNum = 0;
            for (XSLFSlide slide : pptx.getSlides()) {
                slideNum++;
                var texts = new java.util.ArrayList<String>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text.strip());
                        }
                    }
                    if (shape instanceof XSLFTable table) {
                        for (int r = 0; r < table.getNumberOfRows(); r++) {
                            var cells = new java.util.ArrayList<String>();
                            for (int c = 0; c < table.getNumberOfColumns(); c++) {
                                cells.add(table.getCell(r, c).getText().strip());
                            }
                            texts.add(String.join(" | ", cells));
                        }
                    }
                }
                if (!texts.isEmpty()) {
                    sb.append("--- Slide %d ---\n".formatted(slideNum));
                    sb.append(String.join("\n", texts)).append("\n\n");
                }
            }
            if (sb.isEmpty()) return "(Presentation contains no text)";
            return sb.toString();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
