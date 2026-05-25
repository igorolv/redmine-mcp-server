package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.Optional;

/**
 * Text-part truncator for a single {@link AttachmentContent}
 * (the value returned by {@code AttachmentTools.getAttachment}).
 */
public class AttachmentContentTextPartsTruncateStep implements CompressionStep<AttachmentContent> {

    private final int perPartChars;

    public AttachmentContentTextPartsTruncateStep(int perPartChars) {
        this.perPartChars = Math.max(0, perPartChars);
    }

    @Override
    public String name() {
        return "attachment-content-text-parts-truncate";
    }

    @Override
    public Optional<Compressed<AttachmentContent>> apply(AttachmentContent value) {
        var res = AttachmentContentRewriter.truncateTextParts(value, perPartChars);
        if (!res.changed()) {
            return Optional.empty();
        }
        String note = "text parts truncated to %d chars (%d parts affected); originals available via localPath"
                .formatted(perPartChars, res.truncatedParts());
        return Optional.of(new Compressed<>(res.content(), note));
    }
}
