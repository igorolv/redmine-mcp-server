package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.ProjectSummary;
import ru.it_spectrum.ai.redmine.mcp.api.ReleaseRisks;
import ru.it_spectrum.ai.redmine.mcp.api.UserWorkload;
import ru.it_spectrum.ai.redmine.mcp.api.VersionChangelog;
import ru.it_spectrum.ai.redmine.mcp.api.VersionComparison;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;

/**
 * Project / release reporting: aggregated project summaries, version changelogs, release-risk
 * assessment, version comparison, and per-user workload. These are the heaviest output schemas in
 * the server and are the lowest-frequency reads, so they sit behind their own
 * {@code redmine-mcp.tools.release-analytics} flag.
 */
@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "release-analytics", havingValue = "true", matchIfMissing = true)
public class ReleaseAnalyticsTools {

    private static final Logger log = LoggerFactory.getLogger(ReleaseAnalyticsTools.class);

    private final AnalysisService analysisService;

    public ReleaseAnalyticsTools(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @McpTool(
            description = "Summarize one project's issue health, optionally scoped to a milestone: complete open/closed " +
            "totals plus status, tracker, priority and assignee distributions, overdue work and estimated versus spent " +
            "hours for the analyzed open-issue set. Use listIssues when issue records are needed instead of aggregates.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ProjectSummary getProjectSummary(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID; from listVersions", required = false) Integer versionId
    ) {
        log.info("Tool call: getProjectSummary (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getProjectSummary(projectId, versionId);
        ToolLogger.completed(log, "getProjectSummary", start);
        return result;
    }

    @McpTool(
            description = "Analyze one user's open-issue workload by project and priority, including overdue count, " +
            "estimated versus spent hours and top issues; omitting userId selects the API-key user. This returns " +
            "aggregates, not issue records; use getMyIssues for the current user or listIssues for a specified user.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public UserWorkload getUserWorkload(
            @McpToolParam(description = "", required = false) Integer userId,
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId
    ) {
        log.info("Tool call: getUserWorkload (userId={}, projectId={})", userId, projectId);
        long start = System.nanoTime();
        var result = analysisService.getUserWorkload(userId, projectId);
        if (result.isEmpty()) {
            var e = new ResourceUnavailableException("current user");
            ToolLogger.failed(log, "getUserWorkload", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getUserWorkload", start);
        return result.get();
    }

    @McpTool(
            description = "Summarize the issue scope of one known version/milestone as open and closed counts with " +
            "issues grouped by tracker. Use getReleaseRisks for readiness risks or listVersions to discover the version ID.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public VersionChangelog getVersionChangelog(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID; from listVersions") int versionId
    ) {
        log.info("Tool call: getVersionChangelog (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getVersionChangelog(projectId, versionId);
        ToolLogger.completed(log, "getVersionChangelog", start);
        return result;
    }

    @McpTool(
            description = "Assess readiness risks for one known version/milestone: open blockers, overdue work, " +
            "high-priority unresolved issues and unassigned tasks, with a risk score. Use getVersionChangelog for " +
            "the milestone issue breakdown rather than risk triage.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ReleaseRisks getReleaseRisks(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID; from listVersions") int versionId
    ) {
        log.info("Tool call: getReleaseRisks (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getReleaseRisks(projectId, versionId);
        ToolLogger.completed(log, "getReleaseRisks", start);
        return result;
    }

    @McpTool(
            description = "Compare the issue scope and completion of two known versions/milestones in one project. " +
            "Returns issues unique to each, shared issues and closure percentages; use listVersions to discover IDs.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public VersionComparison compareVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "First version/milestone ID; from listVersions") int versionId1,
            @McpToolParam(description = "Second version/milestone ID; from listVersions") int versionId2
    ) {
        log.info("Tool call: compareVersions (projectId={}, versionId1={}, versionId2={})",
                projectId, versionId1, versionId2);
        long start = System.nanoTime();
        var result = analysisService.compareVersions(projectId, versionId1, versionId2);
        ToolLogger.completed(log, "compareVersions", start);
        return result;
    }
}
