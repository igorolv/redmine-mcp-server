package ru.it_spectrum.ai.redmine.mcp.service;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionPipeline;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private RedmineClient client;

    @Mock
    private ExtractionPipeline pipeline;

    @TempDir
    private Path dataDir;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        var properties = TestRedmineMcpProperties.withDataDir(dataDir);
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), properties);
        service = new AttachmentService(client, pipeline, new FileTypeDetector(), snapshot, properties);
    }

    // --- find ---

    @Test
    void findShouldReturnEmptyWhenAttachmentMissing() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThat(service.find(99)).isEmpty();
    }

    // --- getAttachment ---

    @Test
    void getAttachmentShouldReturnOriginalPath() {
        byte[] data = "original".getBytes();
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("photo.png"), eq("image/png"), any(Path.class)))
                .thenReturn(List.of());

        var result = service.getAttachment(1, 20);

        assertThat(result.attachment().id()).isEqualTo(20);
        assertThat(Path.of(result.localPath())).exists();
        assertThat(result.fileUri()).startsWith("file:///");
        assertThat(result.localSize()).isEqualTo(data.length);
    }

    @Test
    void getAttachmentShouldReturnExtractedParts() {
        byte[] data = "hello".getBytes();
        var att = attachment(20, "readme.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("readme.txt"), eq("text/plain"), any(Path.class)))
                .thenReturn(List.of(new ExtractedPart(
                        null, null, "text", "PlainTextParser", (long) data.length,
                        "hello", "/tmp/readme.txt", "file:///tmp/readme.txt", null)));

        var result = service.getAttachment(1, 20);

        assertThat(result.textExtracted()).isTrue();
        assertThat(result.parts()).hasSize(1);
        assertThat(result.parts().getFirst().content()).isEqualTo("hello");
        assertThat(result.localPath()).endsWith("20__readme.txt");
    }

    @Test
    void getAttachmentShouldCapSinglePartByConfiguredPerPartChars() {
        byte[] data = "original".getBytes();
        String text = "x".repeat(60_000);
        var att = attachment(20, "large.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("large.txt"), eq("text/plain"), any(Path.class)))
                .thenReturn(List.of(new ExtractedPart(
                        null, null, "text", "PlainTextParser", (long) data.length,
                        text, "/tmp/large.txt", "file:///tmp/large.txt", null)));

        var result = service.getAttachment(1, 20);

        assertThat(result.truncated()).isTrue();
        assertThat(result.parts().getFirst().content())
                .startsWith("x".repeat(30_000))
                .contains("total length: 60000 chars");
    }

    @Test
    void getAttachmentShouldCapMultiPartByConfiguredPerAttachmentChars() {
        byte[] data = "original".getBytes();
        String text = "x".repeat(30_000);
        var att = attachment(20, "archive.zip", "application/zip");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("archive.zip"), eq("application/zip"), any(Path.class)))
                .thenReturn(List.of(
                        new ExtractedPart("a.txt", "archive.zip", "text", "PlainTextParser",
                                (long) text.length(), text, null, null, null),
                        new ExtractedPart("b.txt", "archive.zip", "text", "PlainTextParser",
                                (long) text.length(), text, null, null, null),
                        new ExtractedPart("c.txt", "archive.zip", "text", "PlainTextParser",
                                (long) text.length(), text, null, null, null)));

        var result = service.getAttachment(1, 20);

        assertThat(result.truncated()).isTrue();
        // First two parts get their full per-part budget (30K each = 60K, capped to per-attachment 50K).
        assertThat(result.parts().get(0).content()).hasSize(30_000);
        assertThat(result.parts().get(1).content()).startsWith("x".repeat(20_000));
        // Third part is fully starved.
        assertThat(result.parts().get(2).truncated()).isTrue();
    }

    @Test
    void getAttachmentShouldHonorExplicitMaxCharsAndPartLimit() {
        byte[] data = "original".getBytes();
        String text = "x".repeat(60_000);
        var att = attachment(20, "large.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("large.txt"), eq("text/plain"), any(Path.class)))
                .thenReturn(List.of(new ExtractedPart(
                        null, null, "text", "PlainTextParser", (long) data.length,
                        text, "/tmp/large.txt", "file:///tmp/large.txt", null)));

        var result = service.getAttachment(1, 20, 2_000, 1_000);

        assertThat(result.truncated()).isTrue();
        assertThat(result.parts().getFirst().content())
                .startsWith("x".repeat(1_000))
                .contains("total length: 60000 chars");
    }

    @Test
    void getAttachmentShouldThrowAttachmentNotFound() {
        when(client.getIssue(1)).thenReturn(issue(1, List.of()));
        assertThatThrownBy(() -> service.getAttachment(1, 99))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void getAttachmentShouldReturnMetadataForImage() {
        byte[] data = new byte[]{1, 2, 3};
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(pipeline.extract(any(Path.class), eq("photo.png"), eq("image/png"), any(Path.class)))
                .thenReturn(List.of(new ExtractedPart(
                        null, null, "image", "ImagePassthroughParser", (long) data.length,
                        null, "/tmp/photo.png", "file:///tmp/photo.png",
                        "Image file. Use localPath/fileUri to access the original.")));

        var result = service.getAttachment(1, 20);

        assertThat(result.textExtracted()).isFalse();
        assertThat(result.parts()).hasSize(1);
        assertThat(result.parts().getFirst().extractionType()).isEqualTo("image");
        assertThat(result.localPath()).endsWith("20__photo.png");
        assertThat(result.fileUri()).startsWith("file:///");
    }

    @Test
    void getAttachmentShouldThrowWhenDownloadFails() {
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(null);
        assertThatThrownBy(() -> service.getAttachment(1, 20))
                .isInstanceOf(AttachmentDownloadFailedException.class);
    }

    // --- helpers ---

    private static RedmineAttachment attachment(int id, String filename, String contentType) {
        return new RedmineAttachment(
                id, filename, 100, contentType,
                "http://redmine/attachments/download/" + id + "/" + filename,
                null,
                new IdName(1, "Tester"),
                "2025-01-01"
        );
    }

    private static RedmineIssue issue(int id, List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "proj"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(1, "Author"),
                null,
                null, null, null,
                "Test issue", null,
                null, null, 0,
                null, null, false,
                "2025-01-01", "2025-01-02",
                null, attachments, null, null, null
        );
    }
}
