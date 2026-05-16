package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class AnalysisTools {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTools.class);

    private final AnalysisService analysisService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public AnalysisTools(AnalysisService analysisService, JsonResponses json, ToolErrors errors) {
        this.analysisService = analysisService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get an aggregated summary of a Redmine project. " +
            "Returns total open/closed counts, plus breakdowns by status, tracker, priority, and assignee " +
            "for analyzed open issues; overdue count; estimated vs spent hours. " +
            "Optionally filter by version/milestone. Analysis scans up to 500 open issues and reports truncation.")
    public String getProjectSummary(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId
    ) {
        log.info("Tool call: getProjectSummary (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getProjectSummary(projectId, versionId);
        ToolLogger.completed(log, "getProjectSummary", start);
        return json.write(result);
    }

    @McpTool(description = "Get workload analysis for a user: open issues grouped by project and priority, " +
            "overdue count, estimated vs spent hours, and top issues by priority. " +
            "Defaults to the current authenticated user if no userId is provided. " +
            "Analysis scans up to 500 open issues and reports truncation.")
    public String getUserWorkload(
            @McpToolParam(description = "User ID (optional, defaults to current user)", required = false) Integer userId,
            @McpToolParam(description = "Project identifier to limit scope (optional)", required = false) String projectId
    ) {
        log.info("Tool call: getUserWorkload (userId={}, projectId={})", userId, projectId);
        long start = System.nanoTime();
        var result = analysisService.getUserWorkload(userId, projectId);
        ToolLogger.completed(log, "getUserWorkload", start);
        if (result.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(result.get());
    }

    @McpTool(description = "Get a changelog for a specific version/milestone: issues grouped by tracker, " +
            "with status and summary. Shows both open and closed issues for the version. " +
            "Analysis scans up to 500 issues and reports truncation.")
    public String getVersionChangelog(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        log.info("Tool call: getVersionChangelog (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getVersionChangelog(projectId, versionId);
        ToolLogger.completed(log, "getVersionChangelog", start);
        return json.write(result);
    }

    @McpTool(description = "Trace the blocking dependency chain for an issue. " +
            "Shows what blocks this issue (must be resolved first) and what this issue blocks. " +
            "Follows blocks/blocked_by relations recursively to reveal the critical path, " +
            "bounded by depth 10 and 30 fetched issues.")
    public String getBlockerChain(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        log.info("Tool call: getBlockerChain (issueId={})", issueId);
        long start = System.nanoTime();
        try {
            var result = analysisService.getBlockerChain(issueId);
            ToolLogger.completed(log, "getBlockerChain", start);
            return json.write(result);
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getBlockerChain", start, e.getMessage());
            return errors.notFound(e.resource(), e.id());
        }
    }

    @McpTool(description = "Find open issues that haven't been updated for a specified number of days. " +
            "Sorted by staleness (oldest first). Useful for identifying neglected or forgotten tasks.")
    public String getStaleIssues(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Minimum days since last update, default 30", required = false) Integer daysSinceUpdate,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit
    ) {
        log.info("Tool call: getStaleIssues (projectId={}, daysSinceUpdate={}, limit={})",
                projectId, daysSinceUpdate, limit);
        long start = System.nanoTime();
        var result = analysisService.getStaleIssues(projectId, daysSinceUpdate, limit);
        ToolLogger.completed(log, "getStaleIssues", start);
        return json.write(result);
    }

    @McpTool(description = "Assess release risks for a version/milestone: identifies open blockers, " +
            "overdue issues, high-priority unresolved issues, and unassigned tasks. " +
            "Provides a risk score summary. Analysis scans up to 500 open issues and reports truncation.")
    public String getReleaseRisks(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        log.info("Tool call: getReleaseRisks (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getReleaseRisks(projectId, versionId);
        ToolLogger.completed(log, "getReleaseRisks", start);
        return json.write(result);
    }

    @McpTool(description = "Compare two versions/milestones: shows issues unique to each version, " +
            "shared issues, and status completion percentages. " +
            "Useful for understanding scope changes between releases. " +
            "Analysis scans up to 500 issues per version and reports truncation.")
    public String compareVersions(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "First version/milestone ID") int versionId1,
            @McpToolParam(description = "Second version/milestone ID") int versionId2
    ) {
        log.info("Tool call: compareVersions (projectId={}, versionId1={}, versionId2={})",
                projectId, versionId1, versionId2);
        long start = System.nanoTime();
        var result = analysisService.compareVersions(projectId, versionId1, versionId2);
        ToolLogger.completed(log, "compareVersions", start);
        return json.write(result);
    }
}
