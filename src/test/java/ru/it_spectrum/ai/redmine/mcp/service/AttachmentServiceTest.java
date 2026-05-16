package ru.it_spectrum.ai.redmine.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private RedmineClient client;

    @Mock
    private DocumentTextExtractor extractor;

    @TempDir
    private Path dataDir;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), new RedmineMcpProperties(dataDir.toString()));
        service = new AttachmentService(client, extractor, snapshot);
    }

    // --- find ---

    @Test
    void findShouldReturnEmptyWhenAttachmentMissing() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThat(service.find(99)).isEmpty();
    }

    @Test
    void findOrThrowShouldThrowAttachmentNotFound() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThatThrownBy(() -> service.findOrThrow(99))
                .isInstanceOf(AttachmentNotFoundException.class)
                .satisfies(e -> assertThat(((AttachmentNotFoundException) e).attachmentId()).isEqualTo(99));
    }

    // --- isImage ---

    @Test
    void isImageShouldDetectByExtension() {
        when(extractor.getFileExtension("photo.png")).thenReturn("png");
        var att = attachment(1, "photo.png", "application/octet-stream");
        assertThat(service.isImage(att)).isTrue();
    }

    @Test
    void isImageShouldDetectByContentType() {
        when(extractor.getFileExtension("blob")).thenReturn("");
        var att = attachment(1, "blob", "image/jpeg");
        assertThat(service.isImage(att)).isTrue();
    }

    @Test
    void isImageShouldReturnFalseForDocument() {
        when(extractor.getFileExtension("report.pdf")).thenReturn("pdf");
        var att = attachment(1, "report.pdf", "application/pdf");
        assertThat(service.isImage(att)).isFalse();
    }

    // --- materializeFile / readContext ---

    @Test
    void materializeFileShouldReturnOriginalPath() throws Exception {
        byte[] data = "original".getBytes();
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);

        var result = service.materializeFile(1, 20);

        assertThat(result.attachment().id()).isEqualTo(20);
        assertThat(Path.of(result.localPath())).exists();
        assertThat(result.fileUri()).startsWith("file:///");
        assertThat(result.localSize()).isEqualTo(data.length);
    }

    @Test
    void readContextShouldReturnExtractedParts() {
        byte[] data = "hello".getBytes();
        var att = attachment(20, "readme.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(extractor.isTextExtractable("readme.txt", "text/plain")).thenReturn(true);
        when(extractor.detectExtractionType(att)).thenReturn("text");
        when(client.downloadAttachment(att.contentUrl())).thenReturn(data);
        when(extractor.extractTextParts(att, Path.of(dataDir.toString(), "issues", "1", "attachments", "20__readme.txt")))
                .thenReturn(List.of(new DocumentTextExtractor.ExtractedTextPart(
                        null, "text", (long) data.length, "hello", null)));

        var result = service.readContext(1, 20);

        assertThat(result.textExtracted()).isTrue();
        assertThat(result.parts()).hasSize(1);
        assertThat(result.parts().getFirst().content()).isEqualTo("hello");
        assertThat(result.localPath()).endsWith("20__readme.txt");
    }

    @Test
    void materializeFileShouldThrowAttachmentNotFound() {
        when(client.getIssue(1)).thenReturn(issue(1, List.of()));
        assertThatThrownBy(() -> service.materializeFile(1, 99))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void readContextShouldReturnMetadataForImage() {
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(extractor.isTextExtractable("photo.png", "image/png")).thenReturn(false);
        when(extractor.getFileExtension("photo.png")).thenReturn("png");

        var result = service.readContext(1, 20);

        assertThat(result.textExtracted()).isFalse();
        assertThat(result.parts()).isEmpty();
        assertThat(result.note()).contains("getAttachmentFile");
    }

    @Test
    void materializeFileShouldThrowWhenDownloadFails() {
        var att = attachment(20, "photo.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(client.downloadAttachment(att.contentUrl())).thenReturn(null);
        assertThatThrownBy(() -> service.materializeFile(1, 20))
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



