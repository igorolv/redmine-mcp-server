package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Materialised attachment: the original file has been downloaded to local storage and parsed into LLM-ready `parts`. ZIP/DOCX containers can produce nested parts; images and binaries return file-reference parts with localPath/fileUri.")
public record AttachmentContent(
        @Schema(description = "Attachment metadata.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        Attachment attachment,
        @Schema(description = "Absolute filesystem path of the downloaded file.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String localPath,
        @Schema(description = "RFC 3986 `file://` URI pointing at the downloaded file.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String fileUri,
        @Schema(description = "Size of the downloaded file on disk, in bytes (-1 if the size could not be determined).", requiredMode = Schema.RequiredMode.REQUIRED)
        long localSize,
        @Schema(description = "Detected document kind used for extraction (`image`, `pdf`, `docx`, `xlsx`, `pptx`, `text`, `zip`, ...).", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String extractionType,
        @Schema(description = "True when at least one part contains successfully extracted text.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean textExtracted,
        @Schema(description = "True when at least one part's text was cut to fit response size limits.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Extracted parser output. Text parts carry content; images and unsupported binaries carry localPath/fileUri and an explanatory note.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Part> parts,
        @Schema(description = "Free-text note explaining the result, typically present when text extraction was skipped.", nullable = true)
        String note,
        @Schema(description = "Human-readable notes describing how this response was compressed to fit the response size budget. Null/empty when no compression was applied.", nullable = true)
        List<String> compressionNotes
) {
    public AttachmentContent(Attachment attachment, String localPath, String fileUri, long localSize,
                             String extractionType, boolean textExtracted, boolean truncated,
                             List<Part> parts, String note) {
        this(attachment, localPath, fileUri, localSize, extractionType, textExtracted, truncated,
                parts, note, null);
    }

    public AttachmentContent withParts(List<Part> newParts) {
        return new AttachmentContent(attachment, localPath, fileUri, localSize, extractionType,
                textExtracted, truncated, newParts, note, compressionNotes);
    }

    public AttachmentContent withTruncated(boolean newTruncated) {
        return new AttachmentContent(attachment, localPath, fileUri, localSize, extractionType,
                textExtracted, newTruncated, parts, note, compressionNotes);
    }

    public AttachmentContent withCompressionNotes(List<String> newCompressionNotes) {
        return new AttachmentContent(attachment, localPath, fileUri, localSize, extractionType,
                textExtracted, truncated, parts, note, newCompressionNotes);
    }

    @Schema(description = "Single extracted text segment. For ZIP archives this is one entry; for other formats there is normally a single part.")
    public record Part(
            @Schema(description = "Logical name of the part (file name within a ZIP, or the attachment file name).", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String name,
            @Schema(description = "Logical name of the part this one was extracted from (e.g. the enclosing ZIP file). Null for parts produced directly from the top-level attachment.", nullable = true)
            String parent,
            @Schema(description = "Detected document kind for this specific part.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String extractionType,
            @Schema(description = "Identifier of the parser that produced this part (e.g. `PlainTextParser`, `ZipParser`). Useful for debugging which extractor handled the file.", nullable = true)
            String producer,
            @Schema(description = "True when text was successfully extracted for this part.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean textExtracted,
            @Schema(description = "True when this part's text was cut to fit the response size limit.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean truncated,
            @Schema(description = "Extracted text, null when extraction was skipped or failed.", nullable = true)
            String content,
            @Schema(description = "Absolute filesystem path to the file this part refers to (the source file itself, or an artefact extracted to disk). Null when no underlying file exists (e.g. a manifest-only stub).", nullable = true)
            String localPath,
            @Schema(description = "RFC 3986 `file://` URI matching `localPath`. Null when `localPath` is null.", nullable = true)
            String fileUri,
            @Schema(description = "Free-text note about this part, typically present when extraction was skipped.", nullable = true)
            String note,
            @Schema(description = "Size of the part's source in bytes, when available.", nullable = true)
            Long size
    ) {
    }
}
