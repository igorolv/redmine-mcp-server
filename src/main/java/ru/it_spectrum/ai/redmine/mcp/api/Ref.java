package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;

@Schema(description = "Reference to a Redmine entity (status, tracker, priority, user, project, etc.) by id and human-readable name.")
public record Ref(
        @Schema(description = "Numeric identifier of the referenced entity.", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
        int id,
        @Schema(description = "Human-readable name of the referenced entity.", example = "In Progress")
        String name
) {
    public static Ref from(IdName source) {
        return source == null ? null : new Ref(source.id(), source.name());
    }
}
