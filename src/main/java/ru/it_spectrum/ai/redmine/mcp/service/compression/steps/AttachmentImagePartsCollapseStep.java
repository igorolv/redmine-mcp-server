package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In every {@link AttachmentContent#parts()} list, keeps the first {@code keep}
 * image parts (extractionType=image) and collapses the rest into a single
 * placeholder Part that records the dropped count. The original files remain
 * on disk and reachable via {@code AttachmentContent.localPath}.
 */
public class AttachmentImagePartsCollapseStep implements CompressionStep<IssueFullContext> {

    private static final String IMAGE_TYPE = "image";

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
            var content = att.content();
            if (content == null || content.parts() == null) {
                newAttachments.add(att);
                continue;
            }
            var imageParts = new ArrayList<AttachmentContent.Part>();
            var otherParts = new ArrayList<AttachmentContent.Part>();
            for (var part : content.parts()) {
                if (IMAGE_TYPE.equals(part.extractionType())) {
                    imageParts.add(part);
                } else {
                    otherParts.add(part);
                }
            }
            if (imageParts.size() <= keep) {
                newAttachments.add(att);
                continue;
            }
            int dropped = imageParts.size() - keep;
            totalDropped += dropped;
            var kept = imageParts.subList(0, keep);
            var collapsed = new AttachmentContent.Part(
                    "<%d more image parts collapsed>".formatted(dropped),
                    null,
                    IMAGE_TYPE,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    "Omitted to fit the response budget. Files remain on disk under the parent attachment's localPath.",
                    null
            );
            var merged = new ArrayList<AttachmentContent.Part>(otherParts.size() + kept.size() + 1);
            merged.addAll(otherParts);
            merged.addAll(kept);
            merged.add(collapsed);
            var newContent = new AttachmentContent(
                    content.attachment(),
                    content.localPath(),
                    content.fileUri(),
                    content.localSize(),
                    content.extractionType(),
                    content.textExtracted(),
                    content.truncated(),
                    List.copyOf(merged),
                    content.note()
            );
            newAttachments.add(new ContextAttachment(att.source(), att.sourceIssueId(), newContent));
            changed = true;
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "collapsed %d image parts across attachments; kept first %d in each; files reachable via attachment.localPath"
                .formatted(totalDropped, keep);
        return Optional.of(new Compressed<>(value.withAttachments(List.copyOf(newAttachments)), note));
    }
}
