package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

public record AttachmentFileResult(
        RedmineAttachment attachment,
        String localPath,
        String fileUri,
        long localSize
) {
}
