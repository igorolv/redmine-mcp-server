package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "List of Redmine project versions / milestones. Wraps a JSON array so the MCP outputSchema can be an object.")
public record VersionList(
        @Schema(description = "Project versions.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Version> versions
) {
    public static VersionList of(List<Version> versions) {
        return new VersionList(versions == null ? List.of() : versions);
    }
}
