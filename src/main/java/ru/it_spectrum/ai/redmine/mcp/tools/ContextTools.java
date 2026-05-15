package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class ContextTools {
    private final ContextService contextService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public ContextTools(ContextService contextService, JsonResponses json, ToolErrors errors) {
        this.contextService = contextService;
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
        var result = contextService.getIssueFullContext(issueId);
        if (result.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(result.get());
    }

    @McpTool(description = "Get all sibling issues — tasks with the same parent (epic/story). " +
            "Shows the full scope of the parent feature: which parts are done, in progress, or pending. " +
            "Includes each sibling's status, assignee, progress, due date, and description snippet. " +
            "Useful for understanding context before implementing a task.")
    public String getIssueSiblings(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        try {
            return json.write(contextService.getIssueSiblings(issueId));
        } catch (ResourceNotFoundException e) {
            return errors.notFound(e.resource(), e.id());
        }
    }

    @McpTool(description = "Find closed/resolved issues related to a given issue. " +
            "Searches: direct relations, closed siblings (same parent), and similar closed issues " +
            "in the same project+version+tracker. " +
            "Useful for finding reference implementations and prior solutions before starting work.")
    public String findRelatedClosedIssues(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Maximum results, default 15", required = false) Integer limit
    ) {
        var result = contextService.findRelatedClosedIssues(issueId, limit);
        if (result.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(result.get());
    }

    @McpTool(description = "Find the latest version of a document/attachment by filename pattern. " +
            "Searches across the issue itself, its parent, siblings, and related issues. " +
            "Returns all matching attachments sorted by date (newest first). " +
            "Useful for finding the latest spec, requirements, or design document.")
    public String findLatestAttachment(
            @McpToolParam(description = "Filename pattern to search (case-insensitive substring match, e.g. 'spec', 'ТЗ', 'requirements')") String pattern,
            @McpToolParam(description = "Issue ID to start search from") int issueId,
            @McpToolParam(description = "Also search in project-wide recent issues (true/false, default false)", required = false) Boolean searchProject
    ) {
        var result = contextService.findLatestAttachment(pattern, issueId, searchProject);
        if (result.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(result.get());
    }

    @McpTool(description = "Build a full network of all relation types for an issue. " +
            "Unlike getIssueTree (parent/child only), this traverses ALL relation types: " +
            "relates, blocks/blocked_by, precedes/follows, duplicates, copied_to. " +
            "Shows each related issue with status, assignee, and due date. " +
            "Follows relations up to the specified depth.")
    public String getIssueNetwork(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "How many levels of relations to follow, default 2, max 3", required = false) Integer depth
    ) {
        try {
            return json.write(contextService.getIssueNetwork(issueId, depth));
        } catch (ResourceNotFoundException e) {
            return errors.notFound(e.resource(), e.id());
        }
    }
}
