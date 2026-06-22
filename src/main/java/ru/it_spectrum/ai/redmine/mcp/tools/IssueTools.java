package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssuePage;
import ru.it_spectrum.ai.redmine.mcp.api.Journal;
import ru.it_spectrum.ai.redmine.mcp.api.MyIssues;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.IssueJournalNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;
import ru.it_spectrum.ai.redmine.mcp.compression.IssueCompression;
import ru.it_spectrum.ai.redmine.mcp.focus.IssueFocus;
import ru.it_spectrum.ai.redmine.mcp.focus.ResponseFocus;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "issue", havingValue = "true", matchIfMissing = true)
public class IssueTools {

    private static final Logger log = LoggerFactory.getLogger(IssueTools.class);

    private final IssueService issueService;
    private final RedmineMcpProperties properties;
    private final IssueFocus issueFocus;
    private final IssueCompression issueCompression;

    public IssueTools(IssueService issueService, RedmineMcpProperties properties,
                      IssueFocus issueFocus, IssueCompression issueCompression) {
        this.issueService = issueService;
        this.properties = properties;
        this.issueFocus = issueFocus;
        this.issueCompression = issueCompression;
    }

    @McpTool(
            description = "List issues in Redmine, filtered by project, status, tracker, assignee, " +
            "priority, version, or saved query, with sorting and pagination.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssuePage listIssues(
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "Status filter: open, closed, * (all), or numeric status ID", required = false) String statusId,
            @McpToolParam(description = "Tracker ID (issue type); from listTrackers", required = false) Integer trackerId,
            @McpToolParam(description = "Assigned user ID; from listProjectMembers", required = false) Integer assignedToId,
            @McpToolParam(description = "Priority ID", required = false) Integer priorityId,
            @McpToolParam(description = "Version/milestone ID", required = false) Integer versionId,
            @McpToolParam(description = "Saved query ID; from listQueries", required = false) Integer queryId,
            @McpToolParam(description = "Custom field filters in query-string form, e.g. 'cf_10=rtk&cf_3=502167'", required = false) String customFieldFilters,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc'", required = false) String sort,
            @McpToolParam(description = "Maximum number of results", required = false) Integer limit,
            @McpToolParam(description = "Pagination offset", required = false) Integer offset
    ) {
        log.info("Tool call: listIssues (projectId={}, statusId={}, trackerId={}, assignedToId={}, priorityId={}, versionId={}, queryId={}, customFieldFilters={}, sort={}, limit={}, offset={})",
                projectId, statusId, trackerId, assignedToId, priorityId, versionId, queryId, customFieldFilters, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();
        Map<String, String> parsedCustomFieldFilters;
        try {
            parsedCustomFieldFilters = issueService.parseCustomFieldFilters(customFieldFilters);
        } catch (IllegalArgumentException e) {
            ToolLogger.failed(log, "listIssues", start, e.getMessage());
            throw e;
        }

        var page = issueService.list(projectId, statusId, trackerId, assignedToId,
                priorityId, versionId, queryId, parsedCustomFieldFilters, sort, actualOffset, actualLimit);
        ToolLogger.completed(log, "listIssues", start);
        return page;
    }

    public IssuePage listIssues(String projectId, String statusId, Integer trackerId,
                                Integer assignedToId, Integer priorityId, Integer versionId,
                                Integer queryId, String sort, Integer limit, Integer offset) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                queryId, null, sort, limit, offset);
    }

    @McpTool(
            description = "Search for issues in Redmine using full-text search.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public IssuePage searchIssues(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "Maximum number of results", required = false) Integer limit,
            @McpToolParam(description = "Pagination offset", required = false) Integer offset
    ) {
        log.info("Tool call: searchIssues (query={}, projectId={}, limit={}, offset={})", query, projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = issueService.searchIssues(query, projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "searchIssues", start);
        return result;
    }

    @McpTool(
            description = "List issues assigned to the currently authenticated user — " +
            "no need to call getCurrentUser first.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MyIssues getMyIssues(
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "Status filter: open (default), closed, * (all), or numeric status ID", required = false) String statusId,
            @McpToolParam(description = "Sort field and direction, e.g. 'updated_on:desc'", required = false) String sort,
            @McpToolParam(description = "Maximum number of results", required = false) Integer limit,
            @McpToolParam(description = "Pagination offset", required = false) Integer offset
    ) {
        log.info("Tool call: getMyIssues (projectId={}, statusId={}, sort={}, limit={}, offset={})",
                projectId, statusId, sort, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var maybeResult = issueService.getMyIssues(projectId, statusId, sort, actualOffset, actualLimit);
        if (maybeResult.isEmpty()) {
            var e = new ResourceUnavailableException("current user");
            ToolLogger.failed(log, "getMyIssues", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getMyIssues", start);
        return maybeResult.get();
    }

    @McpTool(
            description = "Get detailed information about a Redmine issue by ID: description, status, assignee, " +
            "dates, subtasks, relations, journals (notes), attachments, and repository changesets visible to the user.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public Issue getIssue(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Response focus: default, implementation (implementation-relevant text and all changeset revisions), timeline (who-did-what-and-when), changesets (issue identity fields and changesets only), or full.", required = false) String focus
    ) {
        log.info("Tool call: getIssue (issueId={}, focus={})", issueId, focus);
        long start = System.nanoTime();
        var maybeIssue = issueService.find(issueId);
        if (maybeIssue.isEmpty()) {
            var e = new IssueNotFoundException(issueId);
            ToolLogger.failed(log, "getIssue", start, e.getMessage());
            throw e;
        }
        var focused = issueFocus.apply(maybeIssue.get(), ResponseFocus.from(focus));
        var compressed = issueCompression.compress(focused);
        ToolLogger.completed(log, "getIssue", start);
        return compressed;
    }

    public Issue getIssue(int issueId) {
        return getIssue(issueId, null);
    }

    @McpTool(
            description = "Get one full, uncompressed journal entry from a Redmine issue by issue ID and journal ID. " +
            "Use this when getIssue compression notes indicate that older journal entries or long notes were shortened.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public Journal getIssueJournal(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Journal entry ID number from the issue history") int journalId
    ) {
        log.info("Tool call: getIssueJournal (issueId={}, journalId={})", issueId, journalId);
        long start = System.nanoTime();
        try {
            var journal = issueService.getJournal(issueId, journalId);
            ToolLogger.completed(log, "getIssueJournal", start);
            return journal;
        } catch (IssueNotFoundException | IssueJournalNotFoundException e) {
            ToolLogger.failed(log, "getIssueJournal", start, e.getMessage());
            throw e;
        }
    }

}
