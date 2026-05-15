package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

import java.util.List;

public record AttachmentListResult(
        int issueId,
        List<RedmineAttachment> attachments
) {
}
