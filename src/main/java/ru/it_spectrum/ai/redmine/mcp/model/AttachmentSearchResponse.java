package ru.it_spectrum.ai.redmine.mcp.model;

public record AttachmentSearchResponse(
        String query,
        Integer issueId,
        String projectId,
        AttachmentSearchResult result
) {
}
