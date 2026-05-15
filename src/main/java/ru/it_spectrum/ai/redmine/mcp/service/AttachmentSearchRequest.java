package ru.it_spectrum.ai.redmine.mcp.service;

public record AttachmentSearchRequest(
        String query,
        Integer issueId,
        String projectId,
        int issueLimit
) {
}
