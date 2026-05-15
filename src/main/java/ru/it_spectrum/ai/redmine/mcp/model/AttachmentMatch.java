package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

public record AttachmentMatch(RedmineAttachment attachment, String source) {
}
