package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

import java.util.List;

public record AttachmentContextResult(
        RedmineAttachment attachment,
        String localPath,
        String fileUri,
        String extractionType,
        boolean textExtracted,
        boolean truncated,
        List<AttachmentContextPart> parts,
        String note
) {
}
