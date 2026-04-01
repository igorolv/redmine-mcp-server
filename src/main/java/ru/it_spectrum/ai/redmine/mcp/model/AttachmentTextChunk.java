package ru.it_spectrum.ai.redmine.mcp.model;

public record AttachmentTextChunk(
        int attachmentId,
        String filename,
        int chunkIndex,
        int chunkCount,
        int startChar,
        int endChar,
        String text
) {
}
