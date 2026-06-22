package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineProject;

import java.util.List;

@Schema(description = "Redmine project. Numeric `status` field and audit timestamps are omitted — they carry low signal for LLM consumers.")
public record Project(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Display name shown in the UI.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String name,
        @Schema(description = "URL-safe project slug. Either this or the numeric id can be used as projectId for other tools.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String identifier,
        @Schema(description = "Project description (Textile/Markdown markup).", nullable = true)
        String description,
        @Schema(description = "Optional homepage URL configured for the project.", nullable = true)
        String homepage,
        @Schema(description = "Parent project reference, null for top-level projects.", nullable = true)
        Ref parent,
        @Schema(description = "True when the project is visible to anonymous users.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isPublic,
        @Schema(description = "Trackers enabled for this project (Bug, Feature, ...).", nullable = true)
        List<Ref> trackers,
        @Schema(description = "Names of enabled Redmine modules (e.g. issue_tracking, wiki, repository).", nullable = true)
        List<String> enabledModules
) {
    public static Project from(RedmineProject source) {
        if (source == null) {
            return null;
        }
        return new Project(
                source.id(),
                source.name(),
                source.identifier(),
                source.description(),
                source.homepage(),
                Ref.from(source.parent()),
                source.isPublic(),
                source.trackers() == null ? null : source.trackers().stream().map(Ref::from).toList(),
                source.enabledModules() == null
                        ? null
                        : source.enabledModules().stream().map(RedmineProject.NameOnly::name).toList()
        );
    }
}
