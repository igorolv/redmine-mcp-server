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
            description = "Get an aggregated summary of a Redmine project. " +
            "Returns total open/closed counts, plus breakdowns by status, tracker, priority, and assignee " +
            "for analyzed open issues; overdue count; estimated vs spent hours. " +
            "Optionally filter by version/milestone. Analysis scans up to the configured issue budget and reports truncation.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ProjectSummary getProjectSummary(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID", required = false) Integer versionId
    ) {
        log.info("Tool call: getProjectSummary (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getProjectSummary(projectId, versionId);
        ToolLogger.completed(log, "getProjectSummary", start);
        return result;
    }

    @McpTool(
            description = "Get workload analysis for a user: open issues grouped by project and priority, " +
            "overdue count, estimated vs spent hours, and top issues by priority. " +
            "Defaults to the current authenticated user if no userId is provided. " +
            "Analysis scans up to the configured issue budget and reports truncation.",
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
            description = "Get a changelog for a specific version/milestone: issues grouped by tracker, " +
            "with status and summary. Shows both open and closed issues for the version. " +
            "Analysis scans up to the configured issue budget and reports truncation.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public VersionChangelog getVersionChangelog(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        log.info("Tool call: getVersionChangelog (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getVersionChangelog(projectId, versionId);
        ToolLogger.completed(log, "getVersionChangelog", start);
        return result;
    }

    @McpTool(
            description = "Assess release risks for a version/milestone: identifies open blockers, " +
            "overdue issues, high-priority unresolved issues, and unassigned tasks. " +
            "Provides a risk score summary. Analysis scans up to the configured issue budget and reports truncation.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ReleaseRisks getReleaseRisks(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        log.info("Tool call: getReleaseRisks (projectId={}, versionId={})", projectId, versionId);
        long start = System.nanoTime();
        var result = analysisService.getReleaseRisks(projectId, versionId);
        ToolLogger.completed(log, "getReleaseRisks", start);
        return result;
    }

    @McpTool(
            description = "Compare two versions/milestones: shows issues unique to each version, " +
            "shared issues, and status completion percentages. " +
            "Useful for understanding scope changes between releases. " +
            "Analysis scans up to the configured issue budget per version and reports truncation.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public VersionComparison compareVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "First version/milestone ID") int versionId1,
            @McpToolParam(description = "Second version/milestone ID") int versionId2
    ) {
        log.info("Tool call: compareVersions (projectId={}, versionId1={}, versionId2={})",
                projectId, versionId1, versionId2);
        long start = System.nanoTime();
        var result = analysisService.compareVersions(projectId, versionId1, versionId2);
        ToolLogger.completed(log, "compareVersions", start);
        return result;
    }
}
