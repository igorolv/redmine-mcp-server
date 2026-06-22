package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;

@Schema(description = "Reference to a Redmine entity (status, tracker, priority, user, project, etc.) by id and human-readable name.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Ref(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String name
) {
    public static Ref from(IdName source) {
        return source == null ? null : new Ref(source.id(), source.name());
    }
}
