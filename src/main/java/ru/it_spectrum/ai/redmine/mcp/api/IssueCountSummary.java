package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issue count breakdown by lifecycle state.")
public record IssueCountSummary(
        @Schema(description = "Number of currently open issues.", requiredMode = Schema.RequiredMode.REQUIRED)
        int open,
        @Schema(description = "Number of issues in a closed/resolved state.", requiredMode = Schema.RequiredMode.REQUIRED)
        int closed,
        @Schema(description = "Total number of issues across both states.", requiredMode = Schema.RequiredMode.REQUIRED)
        int total
) {
}
