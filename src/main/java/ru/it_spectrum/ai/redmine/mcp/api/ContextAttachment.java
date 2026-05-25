package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Attachment included in issue context, annotated with where it came from in the context graph.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextAttachment(
        @Schema(description = "Where the attachment lives in the context graph.", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allowableValues = {"issue", "parent"}, nullable = true)
        String source,
        @Schema(description = "Identifier of the issue the attachment is hosted on.", requiredMode = Schema.RequiredMode.REQUIRED)
        int sourceIssueId,
        @Schema(description = "Materialized attachment content. Text parts may be truncated by full-context inline budgets; image and binary parts carry localPath/fileUri links.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        AttachmentContent content
) {
}
