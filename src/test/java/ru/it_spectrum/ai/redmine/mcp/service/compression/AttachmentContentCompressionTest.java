package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentContentCompressionTest {

    @Test
    void noNotesWhenWithinBudget() {
        var compression = TestCompression.attachmentContentCompression(props(100_000, 5, 10_000));
        var content = content(List.of(textPart("a.txt", "short text")));

        var result = compression.compress(content);

        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void collapsesImagePartsFirst() {
        var compression = TestCompression.attachmentContentCompression(props(800, 2, 10_000));
        var parts = new ArrayList<AttachmentContent.Part>();
        IntStream.range(0, 30).forEach(i -> parts.add(imagePart("img" + i)));

        var result = compression.compress(content(parts));

        assertThat(result.compressionNotes()).isNotNull();
        assertThat(result.compressionNotes().get(0)).contains("collapsed");
        assertThat(result.parts()).hasSize(3);
        assertThat(result.parts().get(2).name()).contains("28 more image parts collapsed");
    }

    @Test
    void truncatesTextWhenImageCollapseInsufficient() {
        var compression = TestCompression.attachmentContentCompression(props(400, 5, 30));
        var parts = List.of(textPart("doc.txt", "x".repeat(2000)));

        var result = compression.compress(content(parts));

        assertThat(result.compressionNotes()).anyMatch(n -> n.contains("text parts truncated"));
        assertThat(result.parts().get(0).truncated()).isTrue();
        assertThat(result.parts().get(0).content()).startsWith("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    }

    private static RedmineMcpProperties props(int budget, int imagePartsKeep, int textPartChars) {
        return new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(budget, 30, textPartChars, 10_000, imagePartsKeep));
    }

    private static AttachmentContent.Part imagePart(String name) {
        return new AttachmentContent.Part(name, null, "image", "ImageParser",
                false, false, null, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static AttachmentContent.Part textPart(String name, String text) {
        return new AttachmentContent.Part(name, null, "text", "PlainTextParser",
                true, false, text, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static AttachmentContent content(List<AttachmentContent.Part> parts) {
        return new AttachmentContent(
                new Attachment(1, "f", 0, null, null, null, null, null),
                "/tmp/f", "file:///tmp/f", 0,
                "image", true, false, List.copyOf(parts), null);
    }
}
