package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class AnalysisTools {
    private final AnalysisService analysisService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public AnalysisTools(AnalysisService analysisService, JsonResponses json, ToolErrors errors) {
        this.analysisService = analysisService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get an aggregated summary of a Redmine project: issue counts by status, " +
            "tracker, priority, and assignee; overdue count; estimated vs spent hours. " +
            "Optionally filter by version/milestone. One call replaces dozens of listIssues calls.")
    public String getProjectSummary(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId
    ) {
        return json.write(analysisService.getProjectSummary(projectId, versionId));
    }

    @McpTool(description = "Get workload analysis for a user: open issues grouped by project and priority, " +
            "overdue count, estimated vs spent hours, and top issues by priority. " +
            "Defaults to the current authenticated user if no userId is provided.")
    public String getUserWorkload(
            @McpToolParam(description = "User ID (optional, defaults to current user)", required = false) Integer userId,
            @McpToolParam(description = "Project identifier to limit scope (optional)", required = false) String projectId
    ) {
        var result = analysisService.getUserWorkload(userId, projectId);
        if (result.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(result.get());
    }

    @McpTool(description = "Get a changelog for a specific version/milestone: all issues grouped by tracker, " +
            "with status and summary. Shows both open and closed issues for the version.")
    public String getVersionChangelog(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        return json.write(analysisService.getVersionChangelog(projectId, versionId));
    }

    @McpTool(description = "Trace the full chain of blocking dependencies for an issue. " +
            "Shows what blocks this issue (must be resolved first) and what this issue blocks. " +
            "Follows blocks/blocked_by relations recursively to reveal the critical path.")
    public String getBlockerChain(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        try {
            return json.write(analysisService.getBlockerChain(issueId));
        } catch (ResourceNotFoundException e) {
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
        return json.write(analysisService.getStaleIssues(projectId, daysSinceUpdate, limit));
    }

    @McpTool(description = "Assess release risks for a version/milestone: identifies open blockers, " +
            "overdue issues, high-priority unresolved issues, and unassigned tasks. " +
            "Provides a risk score summary.")
    public String getReleaseRisks(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        return json.write(analysisService.getReleaseRisks(projectId, versionId));
    }

    @McpTool(description = "Compare two versions/milestones: shows issues unique to each version, " +
            "shared issues, and status completion percentages. " +
            "Useful for understanding scope changes between releases.")
    public String compareVersions(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "First version/milestone ID") int versionId1,
            @McpToolParam(description = "Second version/milestone ID") int versionId2
    ) {
        return json.write(analysisService.compareVersions(projectId, versionId1, versionId2));
    }
}
