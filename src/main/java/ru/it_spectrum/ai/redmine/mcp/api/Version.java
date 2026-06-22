package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

@Schema(description = "Redmine version / milestone. Audit timestamps are omitted — they rarely matter for LLM-level planning.")
public record Version(
        @Schema(description = "Version identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Project the version belongs to.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref project,
        @Schema(description = "Version name as shown in the UI.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String name,
        @Schema(description = "Optional description of the version's scope.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String description,
        @Schema(description = "Lifecycle status of the version.", allowableValues = {"open", "locked", "closed"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String status,
        @Schema(description = "Planned release date in ISO-8601.", format = "date", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String dueDate,
        @Schema(description = "Sharing scope of the version across projects.",
                allowableValues = {"none", "descendants", "hierarchy", "tree", "system"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String sharing,
        @Schema(description = "Wiki page title that holds the release notes for this version, when configured.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String wikiPageTitle
) {
    public static Version from(RedmineVersion source) {
        if (source == null) {
            return null;
        }
        return new Version(
                source.id(),
                Ref.from(source.project()),
                source.name(),
                source.description(),
                source.status(),
                source.dueDate(),
                source.sharing(),
                source.wikiPageTitle()
        );
    }
}
