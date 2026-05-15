package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class ProjectTools {
    private final RedmineClient client;
    private final JsonResponses json;
    private final ToolErrors errors;

    public ProjectTools(RedmineClient client, JsonResponses json, ToolErrors errors) {
        this.client = client;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "List all projects available in Redmine. " +
            "Returns project names, identifiers, and descriptions. Supports pagination.")
    public String listProjects(
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 25;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getProjects(actualOffset, actualLimit);
        return json.write(page);
    }

    @McpTool(description = "Get detailed information about a specific Redmine project. " +
            "Returns project name, description, trackers, enabled modules, and other details.")
    public String getProject(
            @McpToolParam(description = "Project identifier (string slug) or numeric ID") String projectId
    ) {
        var project = client.getProject(projectId);
        if (project == null) {
            return errors.notFound("project", projectId);
        }
        return json.write(project);
    }

    @McpTool(description = "List members of a Redmine project with their roles.")
    public String listProjectMembers(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "Maximum number of results, default 100", required = false) Integer limit,
            @McpToolParam(description = "Offset for pagination, default 0", required = false) Integer offset
    ) {
        int actualLimit = limit != null ? limit : 100;
        int actualOffset = offset != null ? offset : 0;

        var page = client.getProjectMembers(projectId, actualOffset, actualLimit);
        return json.write(page);
    }

    @McpTool(description = "List versions (milestones) of a Redmine project. " +
            "Returns version names, statuses, due dates, and descriptions.")
    public String listVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var versions = client.getProjectVersions(projectId);
        return json.write(versions);
    }
}
