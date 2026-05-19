package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.MembershipPage;
import ru.it_spectrum.ai.redmine.mcp.api.Project;
import ru.it_spectrum.ai.redmine.mcp.api.ProjectPage;
import ru.it_spectrum.ai.redmine.mcp.api.VersionList;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.ProjectService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class ProjectTools {

    private static final Logger log = LoggerFactory.getLogger(ProjectTools.class);

    private final ProjectService projectService;
    private final RedmineMcpProperties properties;

    public ProjectTools(ProjectService projectService, RedmineMcpProperties properties) {
        this.projectService = projectService;
        this.properties = properties;
    }

    @McpTool(
            description = "List all projects available in Redmine. " +
            "Returns project names, identifiers, and descriptions. Supports pagination.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public ProjectPage listProjects(
            @McpToolParam(description = "Maximum number of results, uses configured default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
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
            description = "Get detailed information about a specific Redmine project. " +
            "Returns project name, description, trackers, enabled modules, and other details.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public Project getProject(
            @McpToolParam(description = "Project identifier (string slug) or numeric ID") String projectId
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
            description = "List members of a Redmine project with their roles.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MembershipPage listProjectMembers(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Maximum number of results, uses configured members default when omitted", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
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
            description = "List versions (milestones) of a Redmine project. " +
            "Returns version names, statuses, due dates, and descriptions.",
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
