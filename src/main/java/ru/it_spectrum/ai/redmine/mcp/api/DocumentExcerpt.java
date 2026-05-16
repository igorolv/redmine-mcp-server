package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Text extracted from an issue attachment (PDF, DOCX, XLSX, PPTX, plain text, ZIP) and inlined into the response.")
public record DocumentExcerpt(
        @Schema(description = "The attachment the text was extracted from.", requiredMode = Schema.RequiredMode.REQUIRED)
        Attachment attachment,
        @Schema(description = "Where the attachment lives in the context graph.", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"issue", "parent"})
        String source,
        @Schema(description = "Identifier of the issue the attachment is hosted on.", requiredMode = Schema.RequiredMode.REQUIRED)
        int sourceIssueId,
        @Schema(description = "Detected document kind used for extraction (`pdf`, `docx`, `xlsx`, `pptx`, `text`, `zip`).", requiredMode = Schema.RequiredMode.REQUIRED)
        String extractionType,
        @Schema(description = "Extracted text, possibly truncated to keep the response within size limits.", requiredMode = Schema.RequiredMode.REQUIRED)
        String text,
        @Schema(description = "True when the extracted text was cut to fit the response.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated
) {
}
