package ru.it_spectrum.ai.redmine.mcp.model;

import java.util.List;

public record LatestAttachmentResult(
        String pattern,
        int issueId,
        AttachmentMatch latest,
        List<AttachmentMatch> matches
) {
}
