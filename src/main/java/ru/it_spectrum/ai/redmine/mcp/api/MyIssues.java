package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issues assigned to the currently authenticated Redmine user, together with that user's identity for clarity.")
public record MyIssues(
        @Schema(description = "Authenticated user the issues are filtered against.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        User user,
        @Schema(description = "Page of issues assigned to the user.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        IssuePage page
) {
}
