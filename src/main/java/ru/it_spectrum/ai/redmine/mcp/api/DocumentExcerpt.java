package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "LLM-ready context extracted from an issue attachment. The `parts` field preserves parser output, including nested ZIP/DOCX artefacts and local file links.")
public record DocumentExcerpt(
        @Schema(description = "The attachment the text was extracted from.", requiredMode = Schema.RequiredMode.REQUIRED)
        Attachment attachment,
        @Schema(description = "Where the attachment lives in the context graph.", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"issue", "parent"})
        String source,
        @Schema(description = "Identifier of the issue the attachment is hosted on.", requiredMode = Schema.RequiredMode.REQUIRED)
        int sourceIssueId,
        @Schema(description = "Top-level extraction kind for this excerpt. `mixed` means multiple part kinds were included.", requiredMode = Schema.RequiredMode.REQUIRED)
        String extractionType,
        @Schema(description = "Original parser parts included in this context excerpt. Text-bearing parts may be truncated; file parts can carry localPath/fileUri.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AttachmentContent.Part> parts,
        @Schema(description = "True when at least one included part contains extracted text.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean textExtracted,
        @Schema(description = "True when the extracted text was cut to fit the response.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated
) {
}
