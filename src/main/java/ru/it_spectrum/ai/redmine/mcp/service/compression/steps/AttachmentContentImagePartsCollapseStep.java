package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.Optional;

/**
 * Image-part collapser for a single {@link AttachmentContent}
 * (the value returned by {@code AttachmentTools.getAttachment}).
 */
public class AttachmentContentImagePartsCollapseStep implements CompressionStep<AttachmentContent> {

    private final int keep;

    public AttachmentContentImagePartsCollapseStep(int keep) {
        this.keep = Math.max(0, keep);
    }

    @Override
    public String name() {
        return "attachment-content-image-parts-collapse";
    }

    @Override
    public Optional<Compressed<AttachmentContent>> apply(AttachmentContent value) {
        var res = AttachmentContentRewriter.collapseImageParts(value, keep);
        if (!res.changed()) {
            return Optional.empty();
        }
        String note = "collapsed %d image parts; kept first %d; files reachable via localPath"
                .formatted(res.droppedImageParts(), keep);
        return Optional.of(new Compressed<>(res.content(), note));
    }
}
