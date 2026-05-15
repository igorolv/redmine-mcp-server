package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

public record AttachmentContentResult(
        RedmineAttachment attachment,
        String extractionType,
        boolean textExtracted,
        boolean truncated,
        String content,
        String note
) {
}
