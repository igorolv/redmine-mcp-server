package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Materialised attachment: the original file has been downloaded to local storage and (when supported) its text content extracted into `parts`. ZIP archives produce one part per archive entry; images and binary files come back with `parts=[]` and a `note` explaining how to access the original file.")
public record AttachmentContent(
        @Schema(description = "Attachment metadata.", requiredMode = Schema.RequiredMode.REQUIRED)
        Attachment attachment,
        @Schema(description = "Absolute filesystem path of the downloaded file.", requiredMode = Schema.RequiredMode.REQUIRED)
        String localPath,
        @Schema(description = "RFC 3986 `file://` URI pointing at the downloaded file.", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileUri,
        @Schema(description = "Size of the downloaded file on disk, in bytes (-1 if the size could not be determined).", requiredMode = Schema.RequiredMode.REQUIRED)
        long localSize,
        @Schema(description = "Detected document kind used for extraction (`image`, `pdf`, `docx`, `xlsx`, `pptx`, `text`, `zip`, ...).", requiredMode = Schema.RequiredMode.REQUIRED)
        String extractionType,
        @Schema(description = "True when at least one part contains successfully extracted text.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean textExtracted,
        @Schema(description = "True when at least one part's text was cut to fit response size limits.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Extracted text segments. Empty for images and unsupported binary formats.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Part> parts,
        @Schema(description = "Free-text note explaining the result, typically present when text extraction was skipped.")
        String note
) {

    @Schema(description = "Single extracted text segment. For ZIP archives this is one entry; for other formats there is normally a single part.")
    public record Part(
            @Schema(description = "Logical name of the part (file name within a ZIP, or the attachment file name).", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Detected document kind for this specific part.", requiredMode = Schema.RequiredMode.REQUIRED)
            String extractionType,
            @Schema(description = "True when text was successfully extracted for this part.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean textExtracted,
            @Schema(description = "True when this part's text was cut to fit the response size limit.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean truncated,
            @Schema(description = "Extracted text, null when extraction was skipped or failed.")
            String content,
            @Schema(description = "Free-text note about this part, typically present when extraction was skipped.")
            String note,
            @Schema(description = "Size of the part's source in bytes, when available.")
            Long size
    ) {
    }
}
