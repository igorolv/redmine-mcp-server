package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared rewriting primitives for {@link AttachmentContent}: collapsing
 * extra image parts and truncating overlong text-bearing parts. Both
 * {@code AttachmentContent}-level steps (for {@code getAttachment}) and
 * {@code IssueFullContext}-level steps (for {@code getIssueFullContext})
 * delegate here.
 */
public final class AttachmentContentRewriter {

    static final String IMAGE_TYPE = "image";

    private AttachmentContentRewriter() {
    }

    public record CollapseResult(AttachmentContent content, int droppedImageParts) {
        public boolean changed() { return droppedImageParts > 0; }
    }

    public record TruncateResult(AttachmentContent content, int truncatedParts) {
        public boolean changed() { return truncatedParts > 0; }
    }

    public static CollapseResult collapseImageParts(AttachmentContent content, int keep) {
        if (content == null || content.parts() == null) {
            return new CollapseResult(content, 0);
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
            return new CollapseResult(content, 0);
        }
        int dropped = imageParts.size() - keep;
        var kept = imageParts.subList(0, Math.max(0, keep));
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
        return new CollapseResult(content.withParts(List.copyOf(merged)), dropped);
    }

    public static TruncateResult truncateTextParts(AttachmentContent content, int perPartChars) {
        if (content == null || content.parts() == null) {
            return new TruncateResult(content, 0);
        }
        int truncated = 0;
        var newParts = new ArrayList<AttachmentContent.Part>(content.parts().size());
        for (var part : content.parts()) {
            String text = part.content();
            if (text == null || text.length() <= perPartChars) {
                newParts.add(part);
                continue;
            }
            truncated++;
            newParts.add(new AttachmentContent.Part(
                    part.name(),
                    part.parent(),
                    part.extractionType(),
                    part.producer(),
                    part.textExtracted(),
                    true,
                    text.substring(0, perPartChars)
                            + "\n\n... (truncated by response compressor; total: %d chars)".formatted(text.length()),
                    part.localPath(),
                    part.fileUri(),
                    part.note(),
                    part.size()
            ));
        }
        if (truncated == 0) {
            return new TruncateResult(content, 0);
        }
        var updated = content.withParts(List.copyOf(newParts));
        if (!updated.truncated()) {
            updated = updated.withTruncated(true);
        }
        return new TruncateResult(updated, truncated);
    }
}
