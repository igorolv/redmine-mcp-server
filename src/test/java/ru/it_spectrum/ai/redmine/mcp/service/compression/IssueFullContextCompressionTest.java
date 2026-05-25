package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueFullContextCompressionTest {

    @Test
    void noNotesWhenWithinBudget() {
        var props = props(100_000, 5);
        var compression = TestCompression.contextCompression(props);
        var ctx = contextWith(null, List.of(), List.of());

        var result = compression.compress(ctx);

        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void collapsesImagePartsFirst() {
        var props = props(800, 2);
        var compression = TestCompression.contextCompression(props);
        var parts = new ArrayList<AttachmentContent.Part>();
        IntStream.range(0, 30).forEach(i -> parts.add(imagePart("img" + i)));
        var ctx = contextWith(parts, List.of(), List.of());

        var result = compression.compress(ctx);

        assertThat(result.compressionNotes()).isNotNull();
        assertThat(result.compressionNotes().get(0)).contains("collapsed");
        var newParts = result.attachments().get(0).content().parts();
        assertThat(newParts).hasSize(3);
        assertThat(newParts.get(2).name()).contains("28 more image parts collapsed");
    }

    private static RedmineMcpProperties props(int budget, int imagePartsKeep) {
        return new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(budget, 30, 20, 10_000, 10_000, imagePartsKeep));
    }

    private static AttachmentContent.Part imagePart(String name) {
        return new AttachmentContent.Part(name, null, "image", "ImageParser",
                false, false, null, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static IssueFullContext contextWith(List<AttachmentContent.Part> parts,
                                                List<Issue.Journal> journals,
                                                List<Issue.Journal> recentNotes) {
        var attachments = parts == null ? List.<ContextAttachment>of()
                : List.of(new ContextAttachment("issue", 1,
                        new AttachmentContent(new Attachment(1, "f", 0, null, null, null, null, null),
                                "/tmp/f", "file:///tmp/f", 0, "image", true, false,
                                List.copyOf(parts), null)));
        var issue = new Issue(1, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null, null,
                "subject", "desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals), List.of(), List.of(), List.of(), null);
        return new IssueFullContext(issue, null, List.of(), attachments, List.copyOf(recentNotes),
                new ContextStats(false, false, false), null);
    }
}
