package ru.it_spectrum.ai.redmine.mcp.model;

public record AttachmentSearchRequest(
        String query,
        Integer issueId,
        String projectId,
        int issueLimit
) {
}
