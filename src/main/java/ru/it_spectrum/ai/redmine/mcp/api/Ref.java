package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;

@Schema(description = "Reference to a Redmine entity (status, tracker, priority, user, project, etc.) by id and human-readable name.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Ref(
        @Schema(description = "Numeric identifier of the referenced entity.", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
        int id,
        @Schema(description = "Human-readable name of the referenced entity.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "In Progress", nullable = true)
        String name
) {
    public static Ref from(IdName source) {
        return source == null ? null : new Ref(source.id(), source.name());
    }
}
