package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentTextPartsTruncateStepTest {

    @Test
    void emptyWhenAllPartsBelowLimit() {
        var step = new AttachmentTextPartsTruncateStep(100);
        var ctx = context(List.of(attachment(List.of(textPart("a.txt", "short text")))));
        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void truncatesPartsOverLimit() {
        var step = new AttachmentTextPartsTruncateStep(10);
        var ctx = context(List.of(attachment(List.of(
                textPart("a.txt", "x".repeat(50)),
                textPart("b.txt", "y".repeat(3))
        ))));

        var result = step.apply(ctx).orElseThrow();
        var parts = result.value().attachments().get(0).content().parts();

        assertThat(parts.get(0).truncated()).isTrue();
        assertThat(parts.get(0).content()).startsWith("xxxxxxxxxx");
        assertThat(parts.get(0).content()).contains("total: 50");
        assertThat(parts.get(1).truncated()).isFalse();
        assertThat(result.note()).contains("10 chars (1 parts affected)");
        assertThat(result.value().attachments().get(0).content().truncated()).isTrue();
    }

    private static AttachmentContent.Part textPart(String name, String content) {
        return new AttachmentContent.Part(name, null, "text", "PlainTextParser",
                true, false, content, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static ContextAttachment attachment(List<AttachmentContent.Part> parts) {
        var content = new AttachmentContent(
                new Attachment(1, "f", 0, null, null, null, null, null),
                "/tmp/f", "file:///tmp/f", 0,
                "text", true, false, parts, null);
        return new ContextAttachment("issue", 1, content);
    }

    private static IssueFullContext context(List<ContextAttachment> attachments) {
        return new IssueFullContext(null, null, List.of(), List.copyOf(attachments), List.of(),
                new ContextStats(false, false, false), null);
    }
}
