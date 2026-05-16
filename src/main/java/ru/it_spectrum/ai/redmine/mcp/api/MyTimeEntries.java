package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Time entries logged by the currently authenticated Redmine user, together with that user's identity.")
public record MyTimeEntries(
        @Schema(description = "Authenticated user the entries are filtered against.", requiredMode = Schema.RequiredMode.REQUIRED)
        User user,
        @Schema(description = "Page of time entries logged by the user.", requiredMode = Schema.RequiredMode.REQUIRED)
        TimeEntryPage page
) {
}
