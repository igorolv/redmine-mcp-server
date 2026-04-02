package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;

@Service
public class ProjectTools {
    private final RedmineClient client;

    public ProjectTools(RedmineClient client) {
        this.client = client;
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

        var sb = new StringBuilder();
        sb.append("Projects: %d total (showing %d-%d)\n\n".formatted(
                page.totalCount(), page.offset() + 1,
                page.offset() + page.projects().size()));

        for (var project : page.projects()) {
            sb.append("- %s [%s] (id: %d)\n".formatted(project.name(), project.identifier(), project.id()));
            if (project.description() != null && !project.description().isBlank()) {
                sb.append("  %s\n".formatted(project.description().length() > 100
                        ? project.description().substring(0, 100) + "..."
                        : project.description()));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get detailed information about a specific Redmine project. " +
            "Returns project name, description, trackers, enabled modules, and other details.")
    public String getProject(
            @McpToolParam(description = "Project identifier (string slug) or numeric ID") String projectId
    ) {
        var project = client.getProject(projectId);
        if (project == null) {
            return "Project '%s' not found".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Project: %s\n".formatted(project.name()));
        sb.append("Identifier: %s\n".formatted(project.identifier()));
        sb.append("ID: %d\n".formatted(project.id()));
        if (project.parent() != null) {
            sb.append("Parent: %s\n".formatted(project.parent().name()));
        }
        if (project.description() != null && !project.description().isBlank()) {
            sb.append("Description: %s\n".formatted(project.description()));
        }
        if (project.homepage() != null && !project.homepage().isBlank()) {
            sb.append("Homepage: %s\n".formatted(project.homepage()));
        }
        sb.append("Public: %s\n".formatted(project.isPublic() ? "yes" : "no"));
        sb.append("Created: %s | Updated: %s\n".formatted(project.createdOn(), project.updatedOn()));

        if (project.trackers() != null && !project.trackers().isEmpty()) {
            sb.append("\nTrackers: %s\n".formatted(
                    project.trackers().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")));
        }
        if (project.enabledModules() != null && !project.enabledModules().isEmpty()) {
            sb.append("Modules: %s\n".formatted(
                    project.enabledModules().stream().map(RedmineProject.NameOnly::name).reduce((a, b) -> a + ", " + b).orElse("")));
        }

        return sb.toString();
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

        var sb = new StringBuilder();
        sb.append("Members of project '%s': %d total\n\n".formatted(projectId, page.totalCount()));

        for (var m : page.memberships()) {
            String member = m.user() != null ? m.user().name() : (m.group() != null ? m.group().name() + " (group)" : "unknown");
            String roles = m.roles() != null
                    ? m.roles().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")
                    : "";
            sb.append("- %s — %s\n".formatted(member, roles));
        }

        return sb.toString();
    }

    @McpTool(description = "List versions (milestones) of a Redmine project. " +
            "Returns version names, statuses, due dates, and descriptions.")
    public String listVersions(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId
    ) {
        var versions = client.getProjectVersions(projectId);

        if (versions.isEmpty()) {
            return "No versions found for project '%s'".formatted(projectId);
        }

        var sb = new StringBuilder();
        sb.append("Versions for project '%s' (%d):\n\n".formatted(projectId, versions.size()));

        for (var v : versions) {
            sb.append("- %s (status: %s".formatted(v.name(), v.status()));
            if (v.dueDate() != null) sb.append(", due: %s".formatted(v.dueDate()));
            sb.append(")\n");
            if (v.description() != null && !v.description().isBlank()) {
                sb.append("  %s\n".formatted(v.description()));
            }
        }

        return sb.toString();
    }
}
