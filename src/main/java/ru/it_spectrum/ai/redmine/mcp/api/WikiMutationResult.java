package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of creating or updating a Redmine wiki page.")
public record WikiMutationResult(
        @Schema(description = "Project identifier or numeric ID used for the request.", requiredMode = Schema.RequiredMode.REQUIRED)
        String projectId,
        @Schema(description = "Wiki page title used in the page path.", requiredMode = Schema.RequiredMode.REQUIRED)
        String pageTitle,
        @Schema(description = "Current wiki page revision after the operation.", requiredMode = Schema.RequiredMode.REQUIRED)
        int version
) {
}
