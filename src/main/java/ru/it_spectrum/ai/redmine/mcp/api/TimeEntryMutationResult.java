package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of creating a Redmine time entry.")
public record TimeEntryMutationResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int timeEntryId
) {
}
