package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Flags indicating which sets of context issues were truncated to keep the response within size limits.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextStats(
        @Schema(description = "True when sibling issues were truncated.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean siblingsTruncated,
        @Schema(description = "True when child issues were truncated.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean childrenTruncated,
        @Schema(description = "True when related issues were truncated.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean relatedTruncated
) {
}
