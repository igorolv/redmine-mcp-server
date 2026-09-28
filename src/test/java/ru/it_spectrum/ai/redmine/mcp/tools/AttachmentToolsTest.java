package ru.it_spectrum.ai.redmine.mcp.tools;

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
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionTestPipelines;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;
import ru.it_spectrum.ai.redmine.mcp.service.compression.TestCompression;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentToolsTest {

    @Mock
    private RedmineClient client;

    @TempDir
    private Path dataDir;

    private AttachmentTools tools;

    @BeforeEach
    void setUp() {
        var properties = TestRedmineMcpProperties.withDataDir(dataDir);
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), properties);
        var service = ExtractionTestPipelines.newAttachmentService(client, snapshot);
        tools = new AttachmentTools(service,
                TestCompression.attachmentContentCompression(properties));
    }

    // --- getAttachment ---

    @Test
    void shouldReturnMaterializedFilePath() throws Exception {
        byte[] data = "original file bytes".getBytes();
        var attachment = attachment(20, "photo.png", "image/png", data.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(data);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 20));

        var json = ToolJsonTestSupport.parse(result);
        assertThat(json.get("attachment").get("filename").asText()).isEqualTo("photo.png");
        Path localPath = Path.of(json.get("localPath").asText());
        assertThat(localPath).exists();
        assertThat(Files.readAllBytes(localPath)).isEqualTo(data);
        assertThat(json.get("fileUri").asText()).startsWith("file:///");
        assertThat(json.get("localSize").asLong()).isEqualTo(data.length);
    }

    @Test
    void shouldReturnImageContextAsFilePart() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        var attachment = attachment(21, "big.png", "image/png", data.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(data);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 21));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(result).contains("big.png");
        assertThat(json.get("parts")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(result).contains("\"extractionType\":\"image\"");
        assertThat(result).contains("\"producer\":\"ImagePassthroughParser\"");
        assertThat(json.get("textExtracted").asBoolean()).isFalse();
        assertThat(result).contains("localPath");
        assertThat(result).contains("fileUri");
    }

    @Test
    void shouldHandleAttachmentNotFound() {
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of()));

        assertThatThrownBy(() -> tools.getAttachment(100, 999))
                .hasMessageContaining("not found");
    }

    @Test
    void shouldHandleAttachmentDownloadFailure() {
        var attachment = attachment(23, "fail.png", "image/png", 5_000);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(null);

        assertThatThrownBy(() -> tools.getAttachment(100, 23))
                .hasMessageContaining("Failed to download attachment");
    }

    // --- text limits ---

    @Test
    void explicitPartLimitAboveCompressionCapIsHonored() throws Exception {
        String text = "abcdefghij".repeat(6_000);
        stubTextAttachment(30, "big.txt", text);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 30, 100_000, 60_000));
        var json = ToolJsonTestSupport.parse(result);

        var part = json.get("parts").get(0);
        assertThat(part.get("content").asText()).isEqualTo(text);
        assertThat(part.get("truncated").asBoolean()).isFalse();
        assertThat(part.has("totalChars")).isFalse();
        assertThat(json.has("compressionNotes")).isFalse();
        assertThat(json.get("limits").get("maxChars").asInt()).isEqualTo(100_000);
        assertThat(json.get("limits").get("partLimit").asInt()).isEqualTo(60_000);
        assertThat(json.get("limits").has("note")).isFalse();
    }

    @Test
    void explicitLimitsAreCappedByServerCeiling() throws Exception {
        int ceiling = RedmineMcpProperties.DEFAULT_ATTACHMENT_MAX_REQUEST_CHARS;
        String text = "abcdefghij".repeat(25_000);
        stubTextAttachment(31, "huge.txt", text);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 31, 500_000, 300_000));
        var json = ToolJsonTestSupport.parse(result);

        var part = json.get("parts").get(0);
        assertThat(part.get("content").asText()).startsWith(text.substring(0, ceiling));
        assertThat(part.get("content").asText()).doesNotContain(text.substring(0, ceiling + 1));
        assertThat(part.get("truncated").asBoolean()).isTrue();
        assertThat(part.get("totalChars").asInt()).isEqualTo(text.length());
        assertThat(json.has("compressionNotes")).isFalse();
        var limits = json.get("limits");
        assertThat(limits.get("maxChars").asInt()).isEqualTo(ceiling);
        assertThat(limits.get("partLimit").asInt()).isEqualTo(ceiling);
        assertThat(limits.get("note").asText())
                .contains("maxChars 500000 reduced to the server ceiling " + ceiling)
                .contains("partLimit 300000 reduced to the server ceiling " + ceiling);
    }

    @Test
    void maxCharsAloneAlsoCapsEachPart() throws Exception {
        String text = "abcdefghij".repeat(4_500);
        stubTextAttachment(32, "spec.txt", text);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 32, 50_000, null));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(json.get("parts").get(0).get("content").asText()).isEqualTo(text);
        assertThat(json.get("limits").get("partLimit").asInt()).isEqualTo(50_000);
    }

    @Test
    void defaultBudgetFilledByTextIsNotCompressed() throws Exception {
        String first = "abcdefghij".repeat(3_000);
        String second = "klmnopqrst".repeat(3_000);
        byte[] zip = zip(Map.of("a.txt", first, "b.txt", second));
        var attachment = attachment(33, "logs.zip", "application/zip", zip.length);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(zip);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 33));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(json.has("compressionNotes")).isFalse();
        assertThat(json.get("limits").get("maxChars").asInt())
                .isEqualTo(RedmineMcpProperties.DEFAULT_ATTACHMENT_PER_ATTACHMENT_CHARS);
        assertThat(result).contains(first);
        int taken = 0;
        for (var part : json.get("parts")) {
            if (part.get("textExtracted").asBoolean()) {
                String content = part.get("content").asText();
                taken += part.has("totalChars") ? content.indexOf("\n\n... (truncated") : content.length();
            }
        }
        assertThat(taken).isEqualTo(RedmineMcpProperties.DEFAULT_ATTACHMENT_PER_ATTACHMENT_CHARS);
    }

    // --- helpers ---

    private void stubTextAttachment(int id, String filename, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        var attachment = attachment(id, filename, "text/plain", data.length);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(data);
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static RedmineAttachment attachment(int id, String filename, String contentType, long size) {
        return new RedmineAttachment(
                id, filename, size, contentType,
                "http://redmine.example.com/attachments/download/" + id + "/" + filename,
                null,
                new IdName(1, "Test User"),
                "2025-01-01T00:00:00Z"
        );
    }

    private static RedmineIssue issueWithAttachments(int id, List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "test-project"),
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
