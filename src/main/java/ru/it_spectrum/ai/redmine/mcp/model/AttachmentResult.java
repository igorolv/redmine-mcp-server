package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

import java.util.List;

public record AttachmentResult(
        RedmineAttachment attachment,
        String localPath,
        String fileUri,
        long localSize,
        String extractionType,
        boolean textExtracted,
        boolean truncated,
        List<AttachmentContextPart> parts,
        String note
) {
}
