package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentImagePartsCollapseStepTest {

    @Test
    void emptyWhenNoAttachments() {
        var step = new AttachmentImagePartsCollapseStep(5);
        var ctx = context(List.of());
        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void emptyWhenImagePartsBelowLimit() {
        var step = new AttachmentImagePartsCollapseStep(5);
        var ctx = context(List.of(attachment(IntStream.range(0, 3)
                .mapToObj(i -> imagePart("img" + i))
                .toList())));
        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void collapsesExtraImagePartsAndKeepsTextParts() {
        var step = new AttachmentImagePartsCollapseStep(2);
        var parts = new ArrayList<AttachmentContent.Part>();
        parts.add(textPart("readme.txt"));
        IntStream.range(0, 5).forEach(i -> parts.add(imagePart("img" + i)));

        var ctx = context(List.of(attachment(parts)));

        var result = step.apply(ctx).orElseThrow();
        var newParts = result.value().attachments().get(0).content().parts();

        assertThat(newParts).hasSize(1 + 2 + 1);
        assertThat(newParts.get(0).extractionType()).isEqualTo("text");
        assertThat(newParts.get(1).name()).isEqualTo("img0");
        assertThat(newParts.get(2).name()).isEqualTo("img1");
        assertThat(newParts.get(3).name()).contains("3 more image parts collapsed");
        assertThat(result.note()).contains("collapsed 3 image parts");
    }

    private static AttachmentContent.Part imagePart(String name) {
        return new AttachmentContent.Part(name, null, "image", "ImageParser",
                false, false, null, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static AttachmentContent.Part textPart(String name) {
        return new AttachmentContent.Part(name, null, "text", "PlainTextParser",
                true, false, "hello", "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static ContextAttachment attachment(List<AttachmentContent.Part> parts) {
        var content = new AttachmentContent(
                new Attachment(1, "f", 0, null, null, null, null, null),
                "/tmp/f", "file:///tmp/f", 0,
                "image", true, false, parts, null);
        return new ContextAttachment("issue", 1, content);
    }

    private static IssueFullContext context(List<ContextAttachment> attachments) {
        return new IssueFullContext(null, null, List.of(), List.copyOf(attachments), List.of(),
                new ContextStats(false, false, false), null);
    }
}
