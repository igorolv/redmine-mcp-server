package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.MembershipPage;
import ru.it_spectrum.ai.redmine.mcp.api.Project;
import ru.it_spectrum.ai.redmine.mcp.api.ProjectPage;
import ru.it_spectrum.ai.redmine.mcp.api.VersionList;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ProjectService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "project", havingValue = "true", matchIfMissing = true)
public class ProjectTools {

    private static final Logger log = LoggerFactory.getLogger(ProjectTools.class);

    private final ProjectService projectService;
    private final RedmineMcpProperties properties;

    public ProjectTools(ProjectService projectService, RedmineMcpProperties properties) {
        this.projectService = projectService;
        this.properties = properties;
    }

    @McpTool(
            description = "Discover accessible Redmine projects and their valid identifiers before project-scoped " +
            "operations when the target project is unknown. Returns project summaries; use getProject for one project's details.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ProjectPage listProjects(
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
    ) {
        log.info("Tool call: listProjects (limit={}, offset={})", limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().defaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = projectService.listProjects(actualOffset, actualLimit);
        ToolLogger.completed(log, "listProjects", start);
        return result;
    }

    @McpTool(
            description = "Inspect one known project's configuration and metadata, including description, trackers " +
            "and enabled modules. Use getProjectSummary for aggregated issue metrics, listProjectMembers for people " +
            "or listVersions for milestones.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public Project getProject(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: getProject (projectId={})", projectId);
        long start = System.nanoTime();
        try {
            var result = projectService.getProjectOrThrow(projectId);
            ToolLogger.completed(log, "getProject", start);
            return result;
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getProject", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Discover the users and groups that belong to one project, their IDs and assigned roles. " +
            "Use when choosing or interpreting assignees; getUserWorkload analyzes a user's issues rather than membership.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MembershipPage listProjectMembers(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "", required = false) Integer limit,
            @McpToolParam(description = "", required = false) Integer offset
    ) {
        log.info("Tool call: listProjectMembers (projectId={}, limit={}, offset={})", projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : properties.pagination().membersDefaultLimit();
        int actualOffset = offset != null ? offset : properties.pagination().defaultOffset();

        var result = projectService.listMembers(projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "listProjectMembers", start);
        return result;
    }

    @McpTool(
            description = "Discover versions/milestones of one project and the IDs used by issue filters and release " +
            "analytics. Returns milestone metadata, not issue scope or risk; use getVersionChangelog or getReleaseRisks for those.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public VersionList listVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listVersions (projectId={})", projectId);
        long start = System.nanoTime();
        var result = projectService.listVersions(projectId);
        ToolLogger.completed(log, "listVersions", start);
        return VersionList.of(result);
    }
}
