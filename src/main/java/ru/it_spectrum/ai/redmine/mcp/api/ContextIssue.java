package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Issue that provides context for the issue under analysis, together with the role(s) in which it appears.")
public record ContextIssue(
        @Schema(description = "Full issue payload.", requiredMode = Schema.RequiredMode.REQUIRED)
        Issue issue,
        @Schema(description = "All roles this issue plays relative to the analysed issue (e.g. both a sibling and a related issue).", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ContextRole> roles
) {
}
