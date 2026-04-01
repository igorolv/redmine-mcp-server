package ru.it_spectrum.ai.redmine.mcp.model;

public record AttachmentTextInfo(
        int attachmentId,
        String filename,
        String contentType,
        boolean extractable,
        String extractionType,
        int totalChars,
        int suggestedChunkSize,
        int chunkCount,
        boolean previewTruncated
) {
}
