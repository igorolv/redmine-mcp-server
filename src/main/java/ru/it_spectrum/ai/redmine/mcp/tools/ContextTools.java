package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class ContextTools {

    private static final Logger log = LoggerFactory.getLogger(ContextTools.class);

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
        log.info("Tool call: getIssueFullContext (issueId={})", issueId);
        long start = System.nanoTime();
        var result = contextService.getIssueFullContext(issueId);
        ToolLogger.completed(log, "getIssueFullContext", start);
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
        log.info("Tool call: getIssueSiblings (issueId={})", issueId);
        long start = System.nanoTime();
        try {
            var result = contextService.getIssueSiblings(issueId);
            ToolLogger.completed(log, "getIssueSiblings", start);
            return json.write(result);
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getIssueSiblings", start, e.getMessage());
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
        log.info("Tool call: findRelatedClosedIssues (issueId={}, limit={})", issueId, limit);
        long start = System.nanoTime();
        var result = contextService.findRelatedClosedIssues(issueId, limit);
        ToolLogger.completed(log, "findRelatedClosedIssues", start);
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
        log.info("Tool call: findLatestAttachment (pattern={}, issueId={}, searchProject={})",
                pattern, issueId, searchProject);
        long start = System.nanoTime();
        var result = contextService.findLatestAttachment(pattern, issueId, searchProject);
        ToolLogger.completed(log, "findLatestAttachment", start);
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
        log.info("Tool call: getIssueNetwork (issueId={}, depth={})", issueId, depth);
        long start = System.nanoTime();
        try {
            var result = contextService.getIssueNetwork(issueId, depth);
            ToolLogger.completed(log, "getIssueNetwork", start);
            return json.write(result);
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getIssueNetwork", start, e.getMessage());
            return errors.notFound(e.resource(), e.id());
        }
    }
}
