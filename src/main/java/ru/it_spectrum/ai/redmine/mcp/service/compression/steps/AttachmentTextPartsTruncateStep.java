package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Caps the {@code content} length of each text-bearing Part inside every
 * {@link IssueFullContext} attachment.
 */
public class AttachmentTextPartsTruncateStep implements CompressionStep<IssueFullContext> {

    private final int perPartChars;

    public AttachmentTextPartsTruncateStep(int perPartChars) {
        this.perPartChars = Math.max(0, perPartChars);
    }

    @Override
    public String name() {
        return "attachment-text-parts-truncate";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.attachments() == null || value.attachments().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int truncatedParts = 0;
        var newAttachments = new ArrayList<ContextAttachment>(value.attachments().size());
        for (var att : value.attachments()) {
            var res = AttachmentContentRewriter.truncateTextParts(att.content(), perPartChars);
            if (res.changed()) {
                changed = true;
                truncatedParts += res.truncatedParts();
                newAttachments.add(new ContextAttachment(att.source(), att.sourceIssueId(), res.content()));
            } else {
                newAttachments.add(att);
            }
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "attachment text parts truncated to %d chars (%d parts affected); originals available via localPath"
                .formatted(perPartChars, truncatedParts);
        return Optional.of(new Compressed<>(value.withAttachments(List.copyOf(newAttachments)), note));
    }
}
