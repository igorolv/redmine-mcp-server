package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

public record DocumentExcerpt(
        RedmineAttachment attachment,
        String source,
        int sourceIssueId,
        String extractionType,
        String text,
        boolean truncated
) {
}
