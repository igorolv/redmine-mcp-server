package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchRequest;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.FixedSizeTextChunker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private RedmineClient client;

    @Mock
    private DocumentTextExtractor extractor;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(client, extractor, new FixedSizeTextChunker());
    }

    // --- find / listForIssue ---

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

    @Test
    void listForIssueShouldReturnEmptyWhenIssueMissing() {
        when(client.getIssue(99)).thenReturn(null);
        assertThat(service.listForIssue(99)).isEmpty();
    }

    @Test
    void listForIssueShouldReturnEmptyListWhenNoAttachments() {
        when(client.getIssue(1)).thenReturn(issue(1, null));
        var result = service.listForIssue(1);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void listForIssueShouldReturnAttachments() {
        var att = attachment(10, "spec.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        assertThat(service.listForIssue(1)).hasValueSatisfying(list ->
                assertThat(list).containsExactly(att));
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

    // --- describeText ---

    @Test
    void describeTextShouldReturnInfoForExtractableAttachment() {
        var att = attachment(5, "doc.txt", "text/plain");
        when(client.getAttachment(5)).thenReturn(att);
        when(extractor.extractText(att)).thenReturn("Hello world");
        when(extractor.detectExtractionType(att)).thenReturn("text");

        var info = service.describeText(5);

        assertThat(info.attachmentId()).isEqualTo(5);
        assertThat(info.filename()).isEqualTo("doc.txt");
        assertThat(info.extractable()).isTrue();
        assertThat(info.extractionType()).isEqualTo("text");
        assertThat(info.totalChars()).isEqualTo("Hello world".length());
        assertThat(info.chunkCount()).isEqualTo(1);
        assertThat(info.previewTruncated()).isFalse();
    }

    @Test
    void describeTextShouldMarkPreviewTruncatedAbovePreviewLimit() {
        var att = attachment(5, "huge.txt", "text/plain");
        String text = "x".repeat(AttachmentService.PREVIEW_LIMIT + 100);
        when(client.getAttachment(5)).thenReturn(att);
        when(extractor.extractText(att)).thenReturn(text);
        when(extractor.detectExtractionType(att)).thenReturn("text");

        var info = service.describeText(5);

        assertThat(info.previewTruncated()).isTrue();
    }

    @Test
    void describeTextShouldThrowWhenAttachmentMissing() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThatThrownBy(() -> service.describeText(99))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void describeTextShouldThrowWhenNotExtractable() {
        var att = attachment(5, "blob.bin", "application/octet-stream");
        when(client.getAttachment(5)).thenReturn(att);
        when(extractor.extractText(att)).thenReturn(null);

        assertThatThrownBy(() -> service.describeText(5))
                .isInstanceOf(AttachmentNotExtractableException.class)
                .satisfies(e -> {
                    var ex = (AttachmentNotExtractableException) e;
                    assertThat(ex.attachmentId()).isEqualTo(5);
                    assertThat(ex.filename()).isEqualTo("blob.bin");
                });
    }

    // --- fetchChunk ---

    @Test
    void fetchChunkShouldReturnRequestedChunk() {
        var att = attachment(5, "long.txt", "text/plain");
        when(client.getAttachment(5)).thenReturn(att);
        when(extractor.extractText(att)).thenReturn("Z".repeat(8_000));

        var chunk = service.fetchChunk(5, 0, 2_500);

        assertThat(chunk.attachmentId()).isEqualTo(5);
        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.chunkCount()).isGreaterThanOrEqualTo(2);
        assertThat(chunk.text()).isNotEmpty();
    }

    @Test
    void fetchChunkShouldThrowIaeForOutOfRangeIndex() {
        var att = attachment(5, "tiny.txt", "text/plain");
        when(client.getAttachment(5)).thenReturn(att);
        when(extractor.extractText(att)).thenReturn("short");

        assertThatThrownBy(() -> service.fetchChunk(5, 7, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void fetchChunkShouldThrowWhenAttachmentMissing() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThatThrownBy(() -> service.fetchChunk(99, 0, null))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    // --- renderImage ---

    @Test
    void renderImageShouldReturnRendered() throws Exception {
        var att = attachment(20, "photo.png", "image/png");
        when(client.getAttachment(20)).thenReturn(att);
        when(extractor.getFileExtension("photo.png")).thenReturn("png");
        when(client.downloadAttachment(att.contentUrl())).thenReturn(pngBytes(200, 100));

        var result = service.renderImage(20, null);

        assertThat(result.attachmentId()).isEqualTo(20);
        assertThat(result.data()).isNotEmpty();
        assertThat(result.mimeType()).startsWith("image/");
    }

    @Test
    void renderImageShouldResizeAndSwitchMimeToPng() throws Exception {
        var att = attachment(20, "wide.jpg", "image/jpeg");
        when(client.getAttachment(20)).thenReturn(att);
        when(extractor.getFileExtension("wide.jpg")).thenReturn("jpg");
        when(client.downloadAttachment(att.contentUrl())).thenReturn(pngBytes(3_000, 2_000));

        var result = service.renderImage(20, 800);

        assertThat(result.mimeType()).isEqualTo("image/png");
    }

    @Test
    void renderImageShouldThrowAttachmentNotFound() {
        when(client.getAttachment(99)).thenReturn(null);
        assertThatThrownBy(() -> service.renderImage(99, null))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void renderImageShouldThrowForNonImage() {
        var att = attachment(20, "doc.pdf", "application/pdf");
        when(client.getAttachment(20)).thenReturn(att);
        when(extractor.getFileExtension("doc.pdf")).thenReturn("pdf");
        assertThatThrownBy(() -> service.renderImage(20, null))
                .isInstanceOf(NotAnImageAttachmentException.class)
                .satisfies(e -> assertThat(((NotAnImageAttachmentException) e).filename()).isEqualTo("doc.pdf"));
    }

    @Test
    void renderImageShouldThrowWhenDownloadFails() {
        var att = attachment(20, "photo.png", "image/png");
        when(client.getAttachment(20)).thenReturn(att);
        when(extractor.getFileExtension("photo.png")).thenReturn("png");
        when(client.downloadAttachment(att.contentUrl())).thenReturn(null);
        assertThatThrownBy(() -> service.renderImage(20, null))
                .isInstanceOf(AttachmentDownloadFailedException.class);
    }

    // --- search ---

    @Test
    void searchShouldReturnIssueFoundFalseForMissingIssue() {
        when(client.getIssue(99)).thenReturn(null);
        var request = new AttachmentSearchRequest("anything", 99, null, 10);

        var result = service.search(request);

        assertThat(result.issueFound()).isFalse();
        assertThat(result.issues()).isEmpty();
        assertThat(result.counters().totalMatches()).isZero();
    }

    @Test
    void searchShouldReturnTypedMatches() {
        var att = attachment(50, "spec.txt", "text/plain");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(att)));
        when(extractor.isTextExtractable("spec.txt", "text/plain")).thenReturn(true);
        when(extractor.extractText(att))
                .thenReturn("OAuth and JWT are configured. Refer to OAuth docs.");

        var request = new AttachmentSearchRequest("oauth", 1, null, 10);
        var result = service.search(request);

        assertThat(result.issueFound()).isTrue();
        assertThat(result.issues()).hasSize(1);
        var issueMatch = result.issues().getFirst();
        assertThat(issueMatch.issueId()).isEqualTo(1);
        assertThat(issueMatch.attachments()).hasSize(1);
        var attMatch = issueMatch.attachments().getFirst();
        assertThat(attMatch.attachmentId()).isEqualTo(50);
        assertThat(attMatch.snippets()).hasSize(2);
        assertThat(result.counters().totalMatches()).isEqualTo(2);
        assertThat(result.counters().matchingAttachments()).isEqualTo(1);
        assertThat(result.counters().matchingIssues()).isEqualTo(1);
        assertThat(result.counters().scannedAttachments()).isEqualTo(1);
        assertThat(result.counters().scannedIssues()).isEqualTo(1);
    }

    @Test
    void searchShouldSkipNonExtractableAttachments() {
        var bin = attachment(60, "image.png", "image/png");
        when(client.getIssue(1)).thenReturn(issue(1, List.of(bin)));
        lenient().when(extractor.isTextExtractable("image.png", "image/png")).thenReturn(false);

        var result = service.search(new AttachmentSearchRequest("x", 1, null, 10));

        assertThat(result.issueFound()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(result.counters().scannedAttachments()).isZero();
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

    private static byte[] pngBytes(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
