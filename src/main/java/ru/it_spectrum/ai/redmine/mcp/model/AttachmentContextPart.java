package ru.it_spectrum.ai.redmine.mcp.model;

public record AttachmentContextPart(
        String name,
        String extractionType,
        boolean textExtracted,
        boolean truncated,
        String content,
        String note,
        Long size
) {
}
