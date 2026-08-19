package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueMutationResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int issueId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer journalId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer attachmentId
) {
}
