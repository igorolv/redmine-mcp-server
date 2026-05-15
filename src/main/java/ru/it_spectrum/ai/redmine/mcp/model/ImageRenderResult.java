package ru.it_spectrum.ai.redmine.mcp.model;

public record ImageRenderResult(
        int attachmentId,
        String filename,
        String contentType,
        long size,
        byte[] data,
        String mimeType
) {
}
