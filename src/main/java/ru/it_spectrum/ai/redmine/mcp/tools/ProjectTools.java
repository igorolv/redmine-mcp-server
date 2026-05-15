package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.ProjectService;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceNotFoundException;

@Service
public class ProjectTools {

    private static final Logger log = LoggerFactory.getLogger(ProjectTools.class);

    private final ProjectService projectService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public ProjectTools(ProjectService projectService, JsonResponses json, ToolErrors errors) {
        this.projectService = projectService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "List all projects available in Redmine. " +
            "Returns project names, identifiers, and descriptions. Supports pagination.")
    public String listProjects(
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listProjects (limit={}, offset={})", limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var result = projectService.listProjects(actualOffset, actualLimit);
        ToolLogger.completed(log, "listProjects", start);
        return json.write(result);
    }

    @McpTool(description = "Get detailed information about a specific Redmine project. " +
            "Returns project name, description, trackers, enabled modules, and other details.")
    public String getProject(
            @McpToolParam(description = "Project identifier (string slug) or numeric ID") String projectId
    ) {
        log.info("Tool call: getProject (projectId={})", projectId);
        long start = System.nanoTime();
        try {
            var result = projectService.getProjectOrThrow(projectId);
            ToolLogger.completed(log, "getProject", start);
            return json.write(result);
        } catch (ResourceNotFoundException e) {
            ToolLogger.failed(log, "getProject", start, e.getMessage());
            return errors.notFound(e.resource(), e.id());
        }
    }

    @McpTool(description = "List members of a Redmine project with their roles.")
    public String listProjectMembers(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Maximum number of results, default 100", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        log.info("Tool call: listProjectMembers (projectId={}, limit={}, offset={})", projectId, limit, offset);
        long start = System.nanoTime();
        int actualLimit = limit != null ? limit : 100;
        int actualOffset = offset != null ? offset : 0;

        var result = projectService.listMembers(projectId, actualOffset, actualLimit);
        ToolLogger.completed(log, "listProjectMembers", start);
        return json.write(result);
    }

    @McpTool(description = "List versions (milestones) of a Redmine project. " +
            "Returns version names, statuses, due dates, and descriptions.")
    public String listVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        log.info("Tool call: listVersions (projectId={})", projectId);
        long start = System.nanoTime();
        var result = projectService.listVersions(projectId);
        ToolLogger.completed(log, "listVersions", start);
        return json.write(result);
    }
}
