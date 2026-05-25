package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Issue that provides context for the issue under analysis, together with the role(s) in which it appears.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextIssue(
        @Schema(description = "Full issue payload.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Issue issue,
        @Schema(description = "All roles this issue plays relative to the analysed issue (e.g. both a sibling and a related issue).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<ContextRole> roles
) {
}
