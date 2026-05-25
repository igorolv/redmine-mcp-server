package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineProject;

import java.util.List;

@Schema(description = "Paginated slice of projects.")
public record ProjectPage(
        @Schema(description = "Projects on this page.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Project> projects,
        @Schema(description = "Total number of projects across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(description = "Zero-based offset of the first project on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Maximum number of projects that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit
) {
    public static ProjectPage from(RedmineProject.Page source) {
        if (source == null) {
            return null;
        }
        var items = source.projects() == null
                ? List.<Project>of()
                : source.projects().stream().map(Project::from).toList();
        return new ProjectPage(items, source.totalCount(), source.offset(), source.limit());
    }
}
