package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Lightweight reference to a related issue (parent, sibling, child, or related-via-relation) with enough metadata for the caller to decide whether to fetch the full issue.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelatedRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int issueId,
        @Schema(description = "Subject (title) of the related issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String subject,
        @Schema(description = "Tracker (Bug, Feature, Task, ...) of the related issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref tracker,
        @Schema(description = "Current status (e.g. New, In Progress, Closed) of the related issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref status,
        @Schema(description = "One or more roles this issue plays relative to the issue under analysis (e.g. both a sibling and a related issue).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<ContextRole> roles
) {
}
