package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Caps the {@code content} length of each text-bearing Part inside materialized
 * attachments. Existing truncation that was applied at extraction time is left
 * alone; this step only kicks in when the full text was still long enough to
 * overflow the response budget.
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
            var content = att.content();
            if (content == null || content.parts() == null) {
                newAttachments.add(att);
                continue;
            }
            var newParts = new ArrayList<AttachmentContent.Part>(content.parts().size());
            boolean attachmentChanged = false;
            for (var part : content.parts()) {
                String text = part.content();
                if (text == null || text.length() <= perPartChars) {
                    newParts.add(part);
                    continue;
                }
                attachmentChanged = true;
                truncatedParts++;
                var truncated = new AttachmentContent.Part(
                        part.name(),
                        part.parent(),
                        part.extractionType(),
                        part.producer(),
                        part.textExtracted(),
                        true,
                        text.substring(0, perPartChars) + "\n\n... (truncated by response compressor; total: %d chars)"
                                .formatted(text.length()),
                        part.localPath(),
                        part.fileUri(),
                        part.note(),
                        part.size()
                );
                newParts.add(truncated);
            }
            if (!attachmentChanged) {
                newAttachments.add(att);
                continue;
            }
            boolean truncatedFlag = content.truncated() || newParts.stream().anyMatch(AttachmentContent.Part::truncated);
            var newContent = new AttachmentContent(
                    content.attachment(),
                    content.localPath(),
                    content.fileUri(),
                    content.localSize(),
                    content.extractionType(),
                    content.textExtracted(),
                    truncatedFlag,
                    List.copyOf(newParts),
                    content.note()
            );
            newAttachments.add(new ContextAttachment(att.source(), att.sourceIssueId(), newContent));
            changed = true;
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "attachment text parts truncated to %d chars (%d parts affected); originals available via localPath"
                .formatted(perPartChars, truncatedParts);
        return Optional.of(new Compressed<>(value.withAttachments(List.copyOf(newAttachments)), note));
    }
}
