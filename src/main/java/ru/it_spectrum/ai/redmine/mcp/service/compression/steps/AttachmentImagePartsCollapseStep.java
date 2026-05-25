package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In every {@link IssueFullContext} attachment, keeps the first {@code keep}
 * image parts and collapses the rest into a single placeholder.
 */
public class AttachmentImagePartsCollapseStep implements CompressionStep<IssueFullContext> {

    private final int keep;

    public AttachmentImagePartsCollapseStep(int keep) {
        this.keep = Math.max(0, keep);
    }

    @Override
    public String name() {
        return "attachment-image-parts-collapse";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.attachments() == null || value.attachments().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int totalDropped = 0;
        var newAttachments = new ArrayList<ContextAttachment>(value.attachments().size());
        for (var att : value.attachments()) {
            var res = AttachmentContentRewriter.collapseImageParts(att.content(), keep);
            if (res.changed()) {
                changed = true;
                totalDropped += res.droppedImageParts();
                newAttachments.add(new ContextAttachment(att.source(), att.sourceIssueId(), res.content()));
            } else {
                newAttachments.add(att);
            }
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "collapsed %d image parts across attachments; kept first %d in each; files reachable via attachment.localPath"
                .formatted(totalDropped, keep);
        return Optional.of(new Compressed<>(value.withAttachments(List.copyOf(newAttachments)), note));
    }
}
