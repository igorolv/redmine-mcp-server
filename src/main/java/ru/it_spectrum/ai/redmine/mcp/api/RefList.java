package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "List of Redmine entity references (id+name pairs). Wraps a JSON array so the MCP outputSchema can be an object.")
public record RefList(
        @Schema(description = "Entity references.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Ref> items
) {
    public static RefList of(List<Ref> items) {
        return new RefList(items == null ? List.of() : items);
    }
}
