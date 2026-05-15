package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.List;

public record AttachmentSearchResult(
        boolean issueFound,
        List<IssueMatches> issues,
        SearchCounters counters
) {

    public record IssueMatches(
            int issueId,
            String subject,
            List<AttachmentMatches> attachments
    ) {
    }

    public record AttachmentMatches(
            int attachmentId,
            String filename,
            String contentType,
            long filesize,
            List<String> snippets
    ) {
    }

    public record SearchCounters(
            int totalMatches,
            int matchingAttachments,
            int matchingIssues,
            int scannedAttachments,
            int scannedIssues
    ) {
    }
}
